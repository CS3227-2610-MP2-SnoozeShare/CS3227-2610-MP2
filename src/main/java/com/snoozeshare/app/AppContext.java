package com.snoozeshare.app;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;

import com.snoozeshare.infra.db.ConnectionFactory;
import com.snoozeshare.infra.db.migration.MigrationRunner;
import com.snoozeshare.infra.events.EventBus;
import com.snoozeshare.infra.events.InProcessEventBus;
import com.snoozeshare.repository.jdbc.JdbcAuditLogRepository;
import com.snoozeshare.repository.jdbc.JdbcAvailabilityBlockRepository;
import com.snoozeshare.repository.jdbc.JdbcBookingRepository;
import com.snoozeshare.repository.jdbc.JdbcPropertyRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketCategoryRepository;
import com.snoozeshare.repository.jdbc.JdbcTicketRepository;
import com.snoozeshare.repository.jdbc.JdbcUserRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletRepository;
import com.snoozeshare.repository.jdbc.JdbcWalletTransactionRepository;
import com.snoozeshare.service.AuditService;
import com.snoozeshare.service.AvailabilityService;
import com.snoozeshare.service.BookingService;
import com.snoozeshare.service.DisputeQueryService;
import com.snoozeshare.service.DisputeSettlementService;
import com.snoozeshare.service.ListingService;
import com.snoozeshare.service.MessageService;
import com.snoozeshare.service.TicketService;
import com.snoozeshare.service.TransactionService;
import com.snoozeshare.service.UserService;
import com.snoozeshare.service.WalletService;
import com.snoozeshare.service.impl.AuditServiceImpl;
import com.snoozeshare.service.impl.AvailabilityServiceImpl;
import com.snoozeshare.service.impl.BookingServiceImpl;
import com.snoozeshare.service.impl.DisputeQueryServiceImpl;
import com.snoozeshare.service.impl.DisputeSettlementServiceImpl;
import com.snoozeshare.service.impl.InMemoryMessageService;
import com.snoozeshare.service.impl.ListingServiceImpl;
import com.snoozeshare.service.impl.TicketServiceImpl;
import com.snoozeshare.service.impl.TransactionServiceImpl;
import com.snoozeshare.service.impl.UserServiceImpl;
import com.snoozeshare.service.impl.WalletServiceImpl;
import com.snoozeshare.session.MockSessionContext;
import com.snoozeshare.session.SessionContext;

public final class AppContext implements AutoCloseable {

    private final Connection connection;
    private final SessionContext session;
    private final UserService userService;
    private final WalletService walletService;
    private final AuditService auditService;
    private final ListingService listingService;
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;
    private final TransactionService transactionService;
    private final EventBus eventBus;
    private final TicketService ticketService;
    private final DisputeQueryService disputeQueryService;
    private final MessageService messageService;
    private final SceneRouter sceneRouter;

    private AppContext(Connection connection) throws SQLException {
        this.connection = connection;
        MigrationRunner.migrate(connection);
        JdbcUserRepository users = new JdbcUserRepository(connection);
        JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
        this.eventBus = new InProcessEventBus();
        this.session = new MockSessionContext();
        this.userService = new UserServiceImpl(connection, users, wallets);
        this.walletService = new WalletServiceImpl(connection, wallets,
                new JdbcWalletTransactionRepository(connection), eventBus);
        this.auditService = new AuditServiceImpl(new JdbcAuditLogRepository(connection), users,
                Clock.systemDefaultZone());
        JdbcPropertyRepository propertyRepo = new JdbcPropertyRepository(connection);
        JdbcAvailabilityBlockRepository blockRepo = new JdbcAvailabilityBlockRepository(connection);
        JdbcBookingRepository bookingRepo = new JdbcBookingRepository(connection);
        this.availabilityService = new AvailabilityServiceImpl(propertyRepo, blockRepo, bookingRepo);
        this.listingService = new ListingServiceImpl(propertyRepo, availabilityService,
                userService, auditService);
        JdbcWalletTransactionRepository txnRepo = new JdbcWalletTransactionRepository(connection);
        this.transactionService = new TransactionServiceImpl(connection, bookingRepo,
                wallets, txnRepo, eventBus);
        this.bookingService = new BookingServiceImpl(connection, bookingRepo, propertyRepo,
                blockRepo, wallets, txnRepo, eventBus);
        JdbcTicketRepository ticketRepo = new JdbcTicketRepository(connection);
        JdbcTicketCategoryRepository categoryRepo = new JdbcTicketCategoryRepository(connection);
        Clock clock = Clock.systemUTC();
        DisputeSettlementService settlementService = new DisputeSettlementServiceImpl(connection,
                ticketRepo, bookingRepo, propertyRepo, users, wallets, txnRepo, auditService,
                eventBus, clock);
        this.ticketService = new TicketServiceImpl(ticketRepo, categoryRepo, bookingRepo, users,
                settlementService, auditService, clock);
        this.disputeQueryService = new DisputeQueryServiceImpl(ticketRepo, bookingRepo, propertyRepo,
                users, txnRepo, clock);
        this.messageService = new InMemoryMessageService(clock);
        this.sceneRouter = new SceneRouter();
    }

    public static AppContext create() throws SQLException {
        return new AppContext(ConnectionFactory.open("jdbc:sqlite::memory:"));
    }

    public static AppContext create(String jdbcUrl) throws SQLException {
        return new AppContext(ConnectionFactory.open(jdbcUrl));
    }

    public TicketService ticketService() {
        return ticketService;
    }

    public DisputeQueryService disputeQueryService() {
        return disputeQueryService;
    }

    public MessageService messageService() {
        return messageService;
    }

    public SessionContext session() {
        return session;
    }

    public UserService userService() {
        return userService;
    }

    public WalletService walletService() {
        return walletService;
    }

    public AuditService auditService() {
        return auditService;
    }

    public ListingService listingService() {
        return listingService;
    }

    public AvailabilityService availabilityService() {
        return availabilityService;
    }

    public BookingService bookingService() {
        return bookingService;
    }

    public TransactionService transactionService() {
        return transactionService;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public SceneRouter sceneRouter() {
        return sceneRouter;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
