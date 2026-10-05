package com.library.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.library.dao.BookDao;
import com.library.dao.BorrowDao;
import com.library.dao.BorrowRequestDao;
import com.library.dao.ReaderDao;
import com.library.entity.Book;
import com.library.entity.BorrowRecord;
import com.library.entity.Reader;
import com.library.util.DateUtil;

/**
 * 借阅业务：借出、还书、续借，以及给记录补算状态/逾期天数/罚金的 decorate()。
 * 罚金规则与 Python 版完全一致：未还时按“今天 - 应还日”实时计算，已还时用库里存的金额。
 */
@Service
public class BorrowService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BorrowService.class);

    /** 业务操作结果：成功与否 + 给用户的提示（对应 Python 版的 flash 消息）。 */
    public record OpResult(boolean ok, String message) {
        public static OpResult ok(String message) {
            return new OpResult(true, message);
        }

        public static OpResult fail(String message) {
            return new OpResult(false, message);
        }
    }

    private final BorrowDao borrowDao;
    private final BookDao bookDao;
    private final ReaderDao readerDao;
    private final BorrowRequestDao requestDao;
    private final SettingsService settings;

    public BorrowService(BorrowDao borrowDao, BookDao bookDao, ReaderDao readerDao,
                         BorrowRequestDao requestDao, SettingsService settings) {
        this.borrowDao = borrowDao;
        this.bookDao = bookDao;
        this.readerDao = readerDao;
        this.requestDao = requestDao;
        this.settings = settings;
    }

    // ------------------------------------------------------------ 状态计算 --

    /** 给借阅记录补上状态、逾期天数、罚金三个计算字段（Python 版 decorate_records）。 */
    public void decorate(List<BorrowRecord> rows) {
        double finePerDay = settings.getDouble("fine_per_day");
        LocalDate today = LocalDate.now();
        for (BorrowRecord r : rows) {
            LocalDate due = DateUtil.parseDate(r.getDueDate());
            LocalDate returned = DateUtil.parseDate(r.getReturnDate());
            if (returned != null) {
                r.setStatus("已归还");
                r.setOverdueDays(due != null ? Math.max(0, (int) (returned.toEpochDay() - due.toEpochDay())) : 0);
                r.setFine(r.getFineAmount() == null ? 0 : Math.round(r.getFineAmount() * 100.0) / 100.0);
                r.setDaysLeft(null);
            } else {
                int overdue = due != null ? Math.max(0, (int) (today.toEpochDay() - due.toEpochDay())) : 0;
                r.setOverdueDays(overdue);
                r.setStatus(overdue > 0 ? "已逾期" : "借出中");
                r.setFine(Math.round(overdue * finePerDay * 100.0) / 100.0);
                r.setDaysLeft(due != null ? (int) (due.toEpochDay() - today.toEpochDay()) : null);
            }
        }
    }

    public BorrowRecord decorateOne(BorrowRecord r) {
        decorate(List.of(r));
        return r;
    }

    // ------------------------------------------------------------ 借出 --

    /** 管理员办理借出。返回结果供控制器转成 flash。 */
    @Transactional
    public OpResult borrowNew(int bookId, int readerId, String dueDateRaw, String remark) {
        Book book = bookDao.findById(bookId);
        Reader reader = readerDao.findById(readerId);
        if (book == null || reader == null) {
            return OpResult.fail("读者或图书已经不存在，请重新选择");
        }
        if (book.getIsDeleted() == 1) {
            return OpResult.fail("《" + book.getTitle() + "》已经下架，不能再借出");
        }
        if (reader.getIsDeleted() == 1) {
            return OpResult.fail(reader.getName() + " 已经注销，不能再借书");
        }
        if (!"正常".equals(reader.getStatus())) {
            return OpResult.fail(reader.getName() + " 当前状态是「" + reader.getStatus() + "」，不能借书");
        }
        int maxBooks = settings.getInt("max_books_per_reader");
        int borrowing = readerDao.borrowingCount(readerId);
        if (borrowing >= maxBooks) {
            return OpResult.fail(reader.getName() + " 已经借了 " + borrowing + " 册，达到上限 "
                    + maxBooks + " 册，请先还书");
        }
        LocalDate due = DateUtil.parseDate(dueDateRaw);
        if (due == null) {
            due = LocalDate.now().plusDays(settings.getInt("borrow_days"));
        }
        if (due.isBefore(LocalDate.now())) {
            return OpResult.fail("应还日期不能早于今天");
        }
        // 原子扣减库存：和读者自助借书走同一条路，把“判断还有没有库存”和“扣减”
        // 合并成一条 UPDATE ... WHERE available_copies > 0，由数据库保证原子性。
        // 否则两个管理员同时借最后一册时，check-then-act 竞态会把库存扣成负数、
        // 还多出一条借阅记录。影响行数 != 1 = 没抢到；写借阅记录若失败，
        // @Transactional 会把这次扣减一起回滚，不会出现“扣了库存没借出”。
        int updated = bookDao.decrementAvailableIfAvailable(bookId);
        if (updated != 1) {
            log.warn("图书 {} 原子扣减失败：库存被并发借完（管理员借出路径）", bookId);
            return OpResult.fail("很抱歉，《" + book.getTitle() + "》刚刚被借完了");
        }
        borrowDao.insert(bookId, readerId, DateUtil.todayStr(), DateUtil.format(due), "管理员", remark);
        // 如果这位读者之前提交过借阅申请，自动标记为已办理
        requestDao.completeFor(readerId, bookId);
        return OpResult.ok("借出成功：《" + book.getTitle() + "》→ " + reader.getName()
                + "，应还日期 " + DateUtil.format(due));
    }

    // ------------------------------------------------------------ 还书 --

    public record ReturnOutcome(double fine, int overdueDays, com.library.entity.BorrowRequest first,
                                List<BorrowRequestDao.WaitingReader> waiting, BorrowRecord record) {
    }

    /**
     * 还书：算罚金、标记归还、库存 +1（不超过总册数）、把排第一的预约置为“可借”。
     * 邮件通知由控制器层决定是否发送（需要拼提示语）。
     */
    @Transactional
    public ReturnOutcome returnBook(int recordId, String fineRaw) {
        BorrowRecord record = borrowDao.findById(recordId);
        if (record == null) {
            return null;
        }
        double finePerDay = settings.getDouble("fine_per_day");
        LocalDate due = DateUtil.parseDate(record.getDueDate());
        String returned = DateUtil.todayStr();
        int overdueDays = 0;
        if (due != null) {
            overdueDays = Math.max(0, (int) (LocalDate.now().toEpochDay() - due.toEpochDay()));
        }
        double fine = 0;
        if (overdueDays > 0) {
            if (fineRaw == null || fineRaw.isBlank()) {
                fine = round2(overdueDays * finePerDay);
            } else {
                try {
                    fine = round2(Math.max(0.0, Double.parseDouble(fineRaw)));
                } catch (NumberFormatException e) {
                    fine = round2(overdueDays * finePerDay);
                }
            }
        }
        borrowDao.markReturned(recordId, returned, fine);
        bookDao.incrementAvailable(record.getBookId());
        var waiting = requestDao.waitingList(record.getBookId());
        var first = requestDao.markFirstWaitingReady(record.getBookId());
        record.setReturnDate(returned);
        record.setFineAmount(fine);
        return new ReturnOutcome(fine, overdueDays, first, waiting, record);
    }

    // ------------------------------------------------------------ 续借 --

    public OpResult renew(int recordId, Integer days, int readerId) {
        BorrowRecord record = readerId > 0
                ? borrowDao.findByIdAndReader(recordId, readerId)
                : borrowDao.findById(recordId);
        if (record == null) {
            return null;   // 404
        }
        int maxRenew = settings.getInt("max_renew_times");
        LocalDate due = DateUtil.parseDate(record.getDueDate());
        if (record.getReturnDate() != null) {
            return OpResult.fail("这本书已经归还，不需要续借");
        }
        if (due != null && due.isBefore(LocalDate.now())) {
            return OpResult.fail("《" + record.getBookTitle() + "》已经逾期，不能续借，请先还书");
        }
        if (record.getRenewCount() >= maxRenew) {
            return OpResult.fail("《" + record.getBookTitle() + "》已续借 " + record.getRenewCount()
                    + " 次，达到上限");
        }
        int borrowDays = settings.getInt("borrow_days");
        if (days == null || days < 1 || days > 365) {
            days = borrowDays;
        }
        LocalDate newDue = (due != null ? due : LocalDate.now()).plusDays(days);
        borrowDao.renew(recordId, DateUtil.format(newDue));
        return OpResult.ok("续借成功：《" + record.getBookTitle() + "》延长 " + days
                + " 天，应还日期改成 " + DateUtil.format(newDue));
    }

    // ------------------------------------------------------------ 读者自助借书 --

    /** 读者在线借书：先原子扣库存（防两人抢最后一册），成功才写借阅记录。 */
    @Transactional
    public OpResult readerSelfBorrow(int bookId, int readerId) {
        Book book = bookDao.findById(bookId);
        if (book == null || book.getIsDeleted() == 1) {
            return OpResult.fail("这本书已经下架了，换一本试试。");
        }
        if (borrowDao.isCurrentlyBorrowing(readerId, bookId)) {
            return OpResult.fail("你已经借了《" + book.getTitle() + "》还没还，不能重复借同一本。");
        }
        int maxBooks = settings.getInt("max_books_per_reader");
        int borrowing = readerDao.borrowingCount(readerId);
        if (borrowing >= maxBooks) {
            return OpResult.fail("你最多能同时借 " + maxBooks + " 册，现在已有 " + borrowing
                    + " 册没还，请先还书。");
        }
        int updated = bookDao.decrementAvailableIfAvailable(bookId);
        if (updated != 1) {
            return OpResult.fail("很抱歉，《" + book.getTitle() + "》刚刚被借完了。");
        }
        int borrowDays = settings.getInt("borrow_days");
        String due = DateUtil.format(LocalDate.now().plusDays(borrowDays));
        borrowDao.insert(bookId, readerId, DateUtil.todayStr(), due, "读者自助", "读者在线自助借出");
        return OpResult.ok("借书成功！《" + book.getTitle() + "》请在 " + borrowDays
                + " 天内归还（" + due + " 前），记得来图书室取书。");
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
