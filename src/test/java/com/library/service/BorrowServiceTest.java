package com.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.library.dao.BookDao;
import com.library.dao.BorrowDao;
import com.library.dao.BorrowRequestDao;
import com.library.dao.ReaderDao;
import com.library.entity.Book;
import com.library.entity.Reader;
import com.library.service.BorrowService.OpResult;

/**
 * 借阅业务的核心规则测试（Mockito 模拟 DAO，不连数据库）。
 *
 * <p>重点锁定一条曾经的真实 bug：管理员办借出原来是“先查库存再无条件扣减”
 * 的 check-then-act 写法，两个管理员并发借最后一册会把库存扣成负数。
 * 修复后与读者自助借书一样走原子扣减（UPDATE ... WHERE available_copies > 0），
 * 这里的用例保证：没抢到库存就绝不写借阅记录、也绝不走无条件扣减的老路。
 */
class BorrowServiceTest {

    private BookDao bookDao;
    private ReaderDao readerDao;
    private BorrowDao borrowDao;
    private BorrowRequestDao requestDao;
    private SettingsService settings;
    private BorrowService service;

    @BeforeEach
    void setUp() {
        bookDao = mock(BookDao.class);
        readerDao = mock(ReaderDao.class);
        borrowDao = mock(BorrowDao.class);
        requestDao = mock(BorrowRequestDao.class);
        settings = mock(SettingsService.class);
        when(settings.getInt("borrow_days")).thenReturn(30);
        when(settings.getInt("max_books_per_reader")).thenReturn(5);
        when(settings.getInt("max_renew_times")).thenReturn(1);
        when(settings.getDouble("fine_per_day")).thenReturn(0.5);
        service = new BorrowService(borrowDao, bookDao, readerDao, requestDao, settings);
    }

    private Book book(int availableCopies) {
        Book b = new Book();
        b.setId(1);
        b.setTitle("三体");
        b.setTotalCopies(3);
        b.setAvailableCopies(availableCopies);
        b.setIsDeleted(0);
        return b;
    }

    private Reader reader() {
        Reader r = new Reader();
        r.setId(2);
        r.setName("王小明");
        r.setStatus("正常");
        r.setIsDeleted(0);
        return r;
    }

    @Test
    void 借出成功_走原子扣减并写记录() {
        when(bookDao.findById(1)).thenReturn(book(1));
        when(readerDao.findById(2)).thenReturn(reader());
        when(readerDao.borrowingCount(2)).thenReturn(0);
        when(bookDao.decrementAvailableIfAvailable(1)).thenReturn(1);

        OpResult result = service.borrowNew(1, 2, null, "");

        assertTrue(result.ok());
        verify(bookDao).decrementAvailableIfAvailable(1);
        verify(borrowDao).insert(eq(1), eq(2), anyString(), anyString(), eq("管理员"), anyString());
        verify(requestDao).completeFor(2, 1);
        // 锁定修复：不允许再出现“无条件扣减”的老写法
        verify(bookDao, never()).decrementAvailable(anyInt());
    }

    @Test
    void 原子扣减没抢到_绝不写借阅记录() {
        when(bookDao.findById(1)).thenReturn(book(1));
        when(readerDao.findById(2)).thenReturn(reader());
        when(readerDao.borrowingCount(2)).thenReturn(0);
        // 另一个管理员刚把最后一册抢走：原子扣减影响行数为 0
        when(bookDao.decrementAvailableIfAvailable(1)).thenReturn(0);

        OpResult result = service.borrowNew(1, 2, null, "");

        assertFalse(result.ok());
        assertTrue(result.message().contains("刚刚被借完了"));
        verify(borrowDao, never()).insert(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString());
        verify(requestDao, never()).completeFor(anyInt(), anyInt());
    }

    @Test
    void 库存为零_同样被原子扣减拦下() {
        when(bookDao.findById(1)).thenReturn(book(0));
        when(readerDao.findById(2)).thenReturn(reader());
        when(readerDao.borrowingCount(2)).thenReturn(0);
        when(bookDao.decrementAvailableIfAvailable(1)).thenReturn(0);

        OpResult result = service.borrowNew(1, 2, null, "");

        assertFalse(result.ok());
        verify(borrowDao, never()).insert(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 校验不通过时_不会碰库存() {
        when(bookDao.findById(1)).thenReturn(book(1));

        // 书已下架
        Book deleted = book(1);
        deleted.setIsDeleted(1);
        when(bookDao.findById(1)).thenReturn(deleted);
        OpResult result = service.borrowNew(1, 2, null, "");
        assertFalse(result.ok());
        verify(bookDao, never()).decrementAvailableIfAvailable(anyInt());

        // 读者状态不正常
        when(bookDao.findById(1)).thenReturn(book(1));
        Reader suspended = reader();
        suspended.setStatus("停用");
        when(readerDao.findById(2)).thenReturn(suspended);
        result = service.borrowNew(1, 2, null, "");
        assertFalse(result.ok());
        verify(bookDao, never()).decrementAvailableIfAvailable(anyInt());

        // 超过每人可借上限
        when(readerDao.findById(2)).thenReturn(reader());
        when(readerDao.borrowingCount(2)).thenReturn(5);
        result = service.borrowNew(1, 2, null, "");
        assertFalse(result.ok());
        verify(bookDao, never()).decrementAvailableIfAvailable(anyInt());
    }

    @Test
    void 应还日期早于今天_拒绝且不碰库存() {
        when(bookDao.findById(1)).thenReturn(book(1));
        when(readerDao.findById(2)).thenReturn(reader());
        when(readerDao.borrowingCount(2)).thenReturn(0);

        OpResult result = service.borrowNew(1, 2, "2000-01-01", "");

        assertFalse(result.ok());
        verify(bookDao, never()).decrementAvailableIfAvailable(anyInt());
    }

    @Test
    void 默认应还日期_按设置的借期算() {
        when(bookDao.findById(1)).thenReturn(book(2));
        when(readerDao.findById(2)).thenReturn(reader());
        when(readerDao.borrowingCount(2)).thenReturn(0);
        when(bookDao.decrementAvailableIfAvailable(1)).thenReturn(1);

        OpResult result = service.borrowNew(1, 2, null, "");

        assertTrue(result.ok());
        String expectedDue = LocalDate.now().plusDays(30).toString();
        verify(borrowDao).insert(eq(1), eq(2), anyString(), eq(expectedDue), eq("管理员"), anyString());
    }

    // ---- 续借规则 ----

    private com.library.entity.BorrowRecord record(String dueDate, String returnDate, int renewCount) {
        com.library.entity.BorrowRecord r = new com.library.entity.BorrowRecord();
        r.setId(9);
        r.setBookTitle("三体");
        r.setDueDate(dueDate);
        r.setReturnDate(returnDate);
        r.setRenewCount(renewCount);
        return r;
    }

    @Test
    void 续借_逾期不能续() {
        String past = LocalDate.now().minusDays(3).toString();
        when(borrowDao.findById(9)).thenReturn(record(past, null, 0));

        OpResult result = service.renew(9, null, 0);

        assertFalse(result.ok());
        assertTrue(result.message().contains("逾期"));
        verify(borrowDao, never()).renew(anyInt(), anyString());
    }

    @Test
    void 续借_超过次数上限不能续() {
        String future = LocalDate.now().plusDays(10).toString();
        when(borrowDao.findById(9)).thenReturn(record(future, null, 1));

        OpResult result = service.renew(9, null, 0);

        assertFalse(result.ok());
        assertTrue(result.message().contains("上限"));
        verify(borrowDao, never()).renew(anyInt(), anyString());
    }

    @Test
    void 续借_成功则顺延并累计次数() {
        String due = LocalDate.now().plusDays(10).toString();
        when(borrowDao.findById(9)).thenReturn(record(due, null, 0));

        OpResult result = service.renew(9, 7, 0);

        assertTrue(result.ok());
        String expectedNewDue = LocalDate.now().plusDays(10).plusDays(7).toString();
        verify(borrowDao).renew(9, expectedNewDue);
    }

    @Test
    void 续借_已归还的书不能再续() {
        when(borrowDao.findById(9)).thenReturn(record("2026-01-01", "2026-01-02", 0));

        OpResult result = service.renew(9, null, 0);

        assertFalse(result.ok());
        verify(borrowDao, never()).renew(anyInt(), anyString());
    }
}
