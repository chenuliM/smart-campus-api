package com.smartcampus.store;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DataStore {

    private static DataStore instance = new DataStore();

    private Map<String, Room> rooms = new ConcurrentHashMap<>();
    private Map<String, Sensor> sensors = new ConcurrentHashMap<>();
    private Map<String, List<SensorReading>> readings = new ConcurrentHashMap<>();

    private DataStore() {
        loadInitialData();
    }

    private void loadInitialData() {
        // Pre-populate initial campus data
        Room r1 = new Room("R201", "Engineering Block B", 120);
        rooms.put(r1.getId(), r1);

        Room r2 = new Room("R202", "Computer Lab 3", 35);
        rooms.put(r2.getId(), r2);

        Sensor s1 = new Sensor("S101", "Temperature", "ACTIVE", "R201");
        s1.setCurrentValue(23.8);
        sensors.put(s1.getId(), s1);
        r1.getSensorIds().add(s1.getId());

        Sensor s2 = new Sensor("S102", "Humidity", "ACTIVE", "R201");
        s2.setCurrentValue(52.0);
        sensors.put(s2.getId(), s2);
        r1.getSensorIds().add(s2.getId());

        Sensor s3 = new Sensor("S103", "Temperature", "MAINTENANCE", "R202");
        s3.setCurrentValue(19.5);
        sensors.put(s3.getId(), s3);
        r2.getSensorIds().add(s3.getId());

        SensorReading sr1 = new SensorReading("RD-001", System.currentTimeMillis() - 3600000, 23.1);
        addReading(s1.getId(), sr1);
        SensorReading sr2 = new SensorReading("RD-002", System.currentTimeMillis(), 23.8);
        addReading(s1.getId(), sr2);
    }

    public static DataStore getInstance() {
        return instance;
    }

    // --- Room accessors ---
    public Map<String, Room> getRooms() {
        return rooms;
    }

    public Room getRoom(String id) {
        return rooms.get(id);
    }

    public void addRoom(Room room) {
        rooms.put(room.getId(), room);
    }

    public Room removeRoom(String id) {
        return rooms.remove(id);
    }

    // --- Sensor accessors ---
    public Map<String, Sensor> getSensors() {
        return sensors;
    }

    public Sensor getSensor(String id) {
        return sensors.get(id);
    }

    public void addSensor(Sensor sensor) {
        sensors.put(sensor.getId(), sensor);
    }

    public Sensor removeSensor(String id) {
        return sensors.remove(id);
    }

    // --- Reading accessors ---
    public List<SensorReading> getReadings(String sensorId) {
        return readings.getOrDefault(sensorId, new CopyOnWriteArrayList<>());
    }

    public void addReading(String sensorId, SensorReading reading) {
        readings.computeIfAbsent(sensorId, k -> new CopyOnWriteArrayList<>()).add(reading);
    }
}