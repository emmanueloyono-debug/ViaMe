package com.viame.model;

import com.viame.model.enums.VehicleStatus;

public class Vehicle {
    private long id;
    private long driverId;
    private String plateNumber;
    private String make;
    private String model;
    private int seats;
    private VehicleStatus status;

    public Vehicle(long id, long driverId, String plateNumber, String make, String model, int seats, VehicleStatus status) {
        this.id = id;
        this.driverId = driverId;
        this.plateNumber = plateNumber;
        this.make = make;
        this.model = model;
        this.seats = seats;
        this.status = status != null ? status : VehicleStatus.PENDING;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getDriverId() { return driverId; }
    public void setDriverId(long driverId) { this.driverId = driverId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }

    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
}
