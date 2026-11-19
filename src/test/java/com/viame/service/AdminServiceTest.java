package com.viame.service;

import com.viame.dao.AuditLogDAO;
import com.viame.dao.DBConnection;
import com.viame.dao.DriverDAO;
import com.viame.dao.TransactionManager;
import com.viame.dao.TripDAO;
import com.viame.dao.UserDAO;
import com.viame.dao.VehicleDAO;
import com.viame.dao.impl.DriverDAOImpl;
import com.viame.dao.impl.UserDAOImpl;
import com.viame.dao.impl.VehicleDAOImpl;
import com.viame.exception.EntityNotFoundException;
import com.viame.exception.ValidationException;
import com.viame.model.Driver;
import com.viame.model.PricingRule;
import com.viame.model.Trip;
import com.viame.model.Vehicle;
import com.viame.model.enums.DriverAvailability;
import com.viame.model.enums.DriverVerificationStatus;
import com.viame.model.enums.TripStatus;
import com.viame.model.enums.UserStatus;
import com.viame.model.enums.VehicleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AdminService Unit and Database Integration Tests")
class AdminServiceTest {

    private Driver createdDriver;
    private Vehicle createdVehicle;

    @AfterEach
    void tearDown() {
        if (createdDriver != null && createdDriver.getId() > 0) {
            try (Connection conn = DBConnection.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM audit_log WHERE entity = 'driver_profile' AND entity_id = ?")) {
                stmt.setLong(1, createdDriver.getId());
                stmt.executeUpdate();
            } catch (Exception ignored) {
            }
        }
        if (createdVehicle != null && createdVehicle.getId() > 0) {
            try {
                new VehicleDAOImpl().delete(createdVehicle.getId());
            } catch (Exception ignored) {
            }
        }
        if (createdDriver != null && createdDriver.getId() > 0) {
            try {
                new UserDAOImpl().delete(createdDriver.getId());
            } catch (Exception ignored) {
            }
        }
        createdVehicle = null;
        createdDriver = null;
    }

    @Test
    @DisplayName("getLiveFleetTrips aggregates all five active non-terminal statuses")
    void testGetLiveFleetTripsAggregatesActiveStatuses() {
        DriverDAO driverDAO = mock(DriverDAO.class);
        VehicleDAO vehicleDAO = mock(VehicleDAO.class);
        TripDAO tripDAO = mock(TripDAO.class);
        AuditLogDAO auditLogDAO = mock(AuditLogDAO.class);
        FareCalculationService fareService = mock(FareCalculationService.class);
        TransactionManager txManager = mock(TransactionManager.class);

        Trip t1 = sampleTrip(1L, TripStatus.REQUESTED);
        Trip t2 = sampleTrip(2L, TripStatus.ASSIGNED);
        Trip t3 = sampleTrip(3L, TripStatus.DRIVER_EN_ROUTE);
        Trip t4 = sampleTrip(4L, TripStatus.ARRIVED);
        Trip t5 = sampleTrip(5L, TripStatus.IN_PROGRESS);

        when(tripDAO.findByStatus(TripStatus.REQUESTED)).thenReturn(List.of(t1));
        when(tripDAO.findByStatus(TripStatus.ASSIGNED)).thenReturn(List.of(t2));
        when(tripDAO.findByStatus(TripStatus.DRIVER_EN_ROUTE)).thenReturn(List.of(t3));
        when(tripDAO.findByStatus(TripStatus.ARRIVED)).thenReturn(List.of(t4));
        when(tripDAO.findByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of(t5));

        AdminService service = new AdminService(driverDAO, vehicleDAO, tripDAO, auditLogDAO, fareService, txManager);
        List<Trip> live = service.getLiveFleetTrips();

        assertEquals(5, live.size());
        verify(tripDAO).findByStatus(TripStatus.REQUESTED);
        verify(tripDAO).findByStatus(TripStatus.ASSIGNED);
        verify(tripDAO).findByStatus(TripStatus.DRIVER_EN_ROUTE);
        verify(tripDAO).findByStatus(TripStatus.ARRIVED);
        verify(tripDAO).findByStatus(TripStatus.IN_PROGRESS);
        verify(tripDAO, never()).findByStatus(TripStatus.COMPLETED);
        verify(tripDAO, never()).findByStatus(TripStatus.CANCELLED);
        verify(tripDAO, never()).findByStatus(TripStatus.NO_DRIVER_FOUND);
    }

    @Test
    @DisplayName("approveDriver and rejectDriver throw EntityNotFoundException for unknown driver")
    void testMissingDriverThrowsEntityNotFound() {
        DriverDAO driverDAO = mock(DriverDAO.class);
        VehicleDAO vehicleDAO = mock(VehicleDAO.class);
        TripDAO tripDAO = mock(TripDAO.class);
        AuditLogDAO auditLogDAO = mock(AuditLogDAO.class);
        FareCalculationService fareService = mock(FareCalculationService.class);
        TransactionManager txManager = new TransactionManager() {
            @Override
            public <T> T inTransaction(TransactionalWork<T> work) {
                try {
                    return work.execute(mock(Connection.class));
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        when(driverDAO.findById(999L)).thenReturn(Optional.empty());

        AdminService service = new AdminService(driverDAO, vehicleDAO, tripDAO, auditLogDAO, fareService, txManager);
        assertThrows(EntityNotFoundException.class, () -> service.approveDriver(1L, 999L));
        assertThrows(EntityNotFoundException.class, () -> service.rejectDriver(1L, 999L, "Bad docs"));
    }

    @Test
    @DisplayName("rejectDriver truncates overly long rejection reason to fit audit_log column and delegates tariff methods")
    void testRejectReasonAndTariffDelegation() throws Exception {
        DriverDAO driverDAO = mock(DriverDAO.class);
        VehicleDAO vehicleDAO = mock(VehicleDAO.class);
        TripDAO tripDAO = mock(TripDAO.class);
        AuditLogDAO auditLogDAO = mock(AuditLogDAO.class);
        FareCalculationService fareService = mock(FareCalculationService.class);
        Connection fakeConn = mock(Connection.class);
        TransactionManager txManager = new TransactionManager() {
            @Override
            public <T> T inTransaction(TransactionalWork<T> work) {
                try {
                    return work.execute(fakeConn);
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Driver drv = new Driver(10L, "d@viame.cm", "+237670000010", "h", "s",
                UserStatus.ACTIVE, "Test Driver", "LIC-10", DriverVerificationStatus.PENDING,
                new BigDecimal("5.00"), DriverAvailability.OFFLINE, 4.05, 9.76, BigDecimal.ZERO);
        when(driverDAO.findById(10L)).thenReturn(Optional.of(drv));
        when(driverDAO.updateVerificationStatus(fakeConn, 10L, DriverVerificationStatus.REJECTED)).thenReturn(true);
        when(vehicleDAO.updateStatusByDriver(fakeConn, 10L, VehicleStatus.INACTIVE)).thenReturn(true);

        AdminService service = new AdminService(driverDAO, vehicleDAO, tripDAO, auditLogDAO, fareService, txManager);

        String longReason = "Invalid insurance document and expired driving permit submitted twice";
        assertTrue(service.rejectDriver(1L, 10L, "  " + longReason + "  "));

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogDAO).log(eq(fakeConn), eq(1L), actionCaptor.capture(), eq("driver_profile"), eq(10L));
        assertTrue(actionCaptor.getValue().startsWith("DRIVER_REJECTED:"));
        assertTrue(actionCaptor.getValue().length() <= 60);

        // Blank reason omits colon
        service.rejectDriver(1L, 10L, "   ");
        verify(auditLogDAO).log(fakeConn, 1L, "DRIVER_REJECTED", "driver_profile", 10L);

        // Tariff delegation
        PricingRule rule = new PricingRule(1L, "Day", new BigDecimal("500"), new BigDecimal("200"),
                new BigDecimal("25"), BigDecimal.ONE, true, LocalDateTime.now());
        when(fareService.getActiveRule()).thenReturn(rule);
        when(fareService.updatePricingRule(eq(1L), eq("Night"), any(), any(), any(), any())).thenReturn(rule);

        assertEquals(rule, service.getActivePricingRule());
        assertEquals(rule, service.updateTariff(1L, "Night", new BigDecimal("600"),
                new BigDecimal("250"), new BigDecimal("30"), new BigDecimal("1.20")));
    }

    @Test
    @DisplayName("Database integration: approveDriver and rejectDriver persist verification, vehicle status, and audit logs")
    void testDatabaseApproveAndRejectDriver() throws Exception {
        UserDAO userDAO = new UserDAOImpl();
        DriverDAO driverDAO = new DriverDAOImpl();
        VehicleDAO vehicleDAO = new VehicleDAOImpl();
        AdminService adminService = new AdminService();

        String tag = Long.toString(System.nanoTime() % 1_000_000);
        createdDriver = new Driver(0, "admin.drv." + tag + "@viame.test",
                "+2376" + String.format("%08d", Long.parseLong(tag)),
                "hash", "salt", UserStatus.ACTIVE, "Pending Driver " + tag,
                "LIC-ADM-" + tag, DriverVerificationStatus.PENDING,
                new BigDecimal("5.00"), DriverAvailability.OFFLINE,
                4.051056, 9.767869, BigDecimal.ZERO);
        userDAO.save(createdDriver);

        createdVehicle = new Vehicle(0, createdDriver.getId(), "LT " + tag.substring(0, 3) + " AD",
                "Toyota", "Corolla", 4, VehicleStatus.PENDING);
        vehicleDAO.save(createdVehicle);

        // Verify pending query & vehicle helper & online count
        List<Driver> pending = adminService.getPendingVerificationDrivers();
        assertTrue(pending.stream().anyMatch(d -> d.getId() == createdDriver.getId()));
        assertTrue(adminService.getVehicleForDriver(createdDriver.getId()).isPresent());
        assertTrue(adminService.countOnlineDrivers() >= 1);
        assertFalse(adminService.getAllTrips().isEmpty());

        // Approve
        assertTrue(adminService.approveDriver(1L, createdDriver.getId()));
        assertEquals(DriverVerificationStatus.APPROVED,
                driverDAO.findById(createdDriver.getId()).orElseThrow().getVerificationStatus());
        assertEquals(VehicleStatus.ACTIVE,
                vehicleDAO.findByDriverId(createdDriver.getId()).orElseThrow().getStatus());
        assertFalse(adminService.getPendingVerificationDrivers().stream()
                .anyMatch(d -> d.getId() == createdDriver.getId()));

        // Reject with reason
        assertTrue(adminService.rejectDriver(1L, createdDriver.getId(), "Expired licence"));
        assertEquals(DriverVerificationStatus.REJECTED,
                driverDAO.findById(createdDriver.getId()).orElseThrow().getVerificationStatus());
        assertEquals(VehicleStatus.INACTIVE,
                vehicleDAO.findByDriverId(createdDriver.getId()).orElseThrow().getStatus());

        // Verify audit_log entries exist
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT action FROM audit_log WHERE entity = 'driver_profile' AND entity_id = ? ORDER BY id ASC")) {
            stmt.setLong(1, createdDriver.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("DRIVER_APPROVED", rs.getString("action"));
                assertTrue(rs.next());
                assertEquals("DRIVER_REJECTED:Expired licence", rs.getString("action"));
            }
        }
    }

    private static Trip sampleTrip(long id, TripStatus status) {
        return new Trip(id, 4L, 2L, 1L, 1L,
                4.051056, 9.767869, "Akwa",
                4.075833, 9.718611, "Bonamoussadi",
                new BigDecimal("5.20"), 15, status, 1);
    }

    @Test
    @DisplayName("getLiveFleetTrips sorts newest first, breaks ties by id desc, places null timestamps last, and handles null DAO lists")
    void testGetLiveFleetTripsOrderingAndNullResilience() {
        DriverDAO driverDAO = mock(DriverDAO.class);
        VehicleDAO vehicleDAO = mock(VehicleDAO.class);
        TripDAO tripDAO = mock(TripDAO.class);
        AuditLogDAO auditLogDAO = mock(AuditLogDAO.class);
        FareCalculationService fareService = mock(FareCalculationService.class);
        TransactionManager txManager = mock(TransactionManager.class);

        LocalDateTime tOlder = LocalDateTime.of(2026, 11, 1, 9, 0);
        LocalDateTime tNewer = LocalDateTime.of(2026, 11, 1, 10, 0);

        Trip oldest = sampleTrip(10L, TripStatus.REQUESTED);
        oldest.setRequestedAt(tOlder);
        Trip tieLow = sampleTrip(11L, TripStatus.ASSIGNED);
        tieLow.setRequestedAt(tNewer);
        Trip tieHigh = sampleTrip(15L, TripStatus.IN_PROGRESS);
        tieHigh.setRequestedAt(tNewer);
        Trip nullDate = sampleTrip(20L, TripStatus.ARRIVED);
        nullDate.setRequestedAt(null);

        when(tripDAO.findByStatus(TripStatus.REQUESTED)).thenReturn(List.of(oldest));
        when(tripDAO.findByStatus(TripStatus.ASSIGNED)).thenReturn(List.of(tieLow));
        when(tripDAO.findByStatus(TripStatus.DRIVER_EN_ROUTE)).thenReturn(null);
        when(tripDAO.findByStatus(TripStatus.ARRIVED)).thenReturn(List.of(nullDate));
        when(tripDAO.findByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of(tieHigh));

        AdminService service = new AdminService(driverDAO, vehicleDAO, tripDAO, auditLogDAO, fareService, txManager);
        List<Trip> live = service.getLiveFleetTrips();

        assertEquals(List.of(15L, 11L, 10L, 20L), live.stream().map(Trip::getId).toList());
    }

    @Test
    @DisplayName("rejectDriver handles null, blank, exact 60-char boundary, and 61+ char reasons cleanly")
    void testRejectDriverNullAndBoundaryReasons() throws Exception {
        DriverDAO driverDAO = mock(DriverDAO.class);
        VehicleDAO vehicleDAO = mock(VehicleDAO.class);
        TripDAO tripDAO = mock(TripDAO.class);
        AuditLogDAO auditLogDAO = mock(AuditLogDAO.class);
        FareCalculationService fareService = mock(FareCalculationService.class);
        Connection fakeConn = mock(Connection.class);
        TransactionManager txManager = new TransactionManager() {
            @Override
            public <T> T inTransaction(TransactionalWork<T> work) {
                try {
                    return work.execute(fakeConn);
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Driver drv = new Driver(10L, "d@viame.cm", "+237670000010", "h", "s",
                UserStatus.ACTIVE, "Test Driver", "LIC-10", DriverVerificationStatus.PENDING,
                new BigDecimal("5.00"), DriverAvailability.OFFLINE, 4.05, 9.76, BigDecimal.ZERO);
        when(driverDAO.findById(10L)).thenReturn(Optional.of(drv));
        when(driverDAO.updateVerificationStatus(fakeConn, 10L, DriverVerificationStatus.REJECTED)).thenReturn(true);
        when(vehicleDAO.updateStatusByDriver(fakeConn, 10L, VehicleStatus.INACTIVE)).thenReturn(true);

        AdminService service = new AdminService(driverDAO, vehicleDAO, tripDAO, auditLogDAO, fareService, txManager);

        // Null reason -> "DRIVER_REJECTED"
        assertTrue(service.rejectDriver(1L, 10L, null));
        verify(auditLogDAO).log(fakeConn, 1L, "DRIVER_REJECTED", "driver_profile", 10L);

        // "DRIVER_REJECTED:" is 16 chars; 44 chars fits cleanly at 60 chars without truncation
        String exact44 = "A".repeat(44);
        assertTrue(service.rejectDriver(1L, 10L, exact44));
        verify(auditLogDAO).log(fakeConn, 1L, "DRIVER_REJECTED:" + exact44, "driver_profile", 10L);

        // 45 chars exceeds 60 chars and truncates to exact 60 chars
        String over45 = "B".repeat(45);
        assertTrue(service.rejectDriver(1L, 10L, over45));
        verify(auditLogDAO).log(fakeConn, 1L, ("DRIVER_REJECTED:" + over45).substring(0, 60), "driver_profile", 10L);
    }

    @Test
    @DisplayName("Database integration: approveDriver/rejectDriver throw EntityNotFoundException on nonexistent driver and updateTariff validates inputs")
    void testDatabaseNonexistentDriverAndTariffValidation() {
        AdminService adminService = new AdminService();
        assertThrows(EntityNotFoundException.class, () -> adminService.approveDriver(1L, 9999999L));
        assertThrows(EntityNotFoundException.class, () -> adminService.rejectDriver(1L, 9999999L, "Missing"));
        assertTrue(adminService.getVehicleForDriver(9999999L).isEmpty());

        assertThrows(ValidationException.class, () -> adminService.updateTariff(1L, " ",
                new BigDecimal("500"), new BigDecimal("200"), new BigDecimal("25"), BigDecimal.ONE));
        assertThrows(ValidationException.class, () -> adminService.updateTariff(1L, "Bad Surge",
                new BigDecimal("500"), new BigDecimal("200"), new BigDecimal("25"), new BigDecimal("0.50")));
        assertThrows(ValidationException.class, () -> adminService.updateTariff(1L, "High Surge",
                new BigDecimal("500"), new BigDecimal("200"), new BigDecimal("25"), new BigDecimal("3.50")));
        assertThrows(ValidationException.class, () -> adminService.updateTariff(1L, "Negative Base",
                new BigDecimal("-1"), new BigDecimal("200"), new BigDecimal("25"), BigDecimal.ONE));
    }
}
