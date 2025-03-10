package dto;

// DTO for LiftRide
public class ConsumerLiftRideDto {
    private int liftID;
    private int time;
    private int resortID;
    private String seasonID;
    private String dayID;
    private int skierID;

    // Default constructor (important for JSON serialization)
    public ConsumerLiftRideDto() {}
    // Constructor with fields
    public ConsumerLiftRideDto(Integer liftID, Integer time, int resortID, String seasonID, String dayID, int skierID) {
        this.liftID = liftID;
        this.time = time;
        this.resortID = resortID;
        this.seasonID = seasonID;
        this.dayID = dayID;
        this.skierID = skierID;
    }
    // Getters and setters
    public Integer getLiftID() { return liftID; }
    public void setLiftID(Integer liftID) { this.liftID = liftID; }
    public Integer getTime() { return time; }
    public void setTime(Integer time) { this.time = time; }

    public int getResortID() {
        return resortID;
    }

    public void setResortID(int resortID) {
        this.resortID = resortID;
    }

    public String getSeasonID() {
        return seasonID;
    }

    public void setSeasonID(String seasonID) {
        this.seasonID = seasonID;
    }

    public String getDayID() {
        return dayID;
    }

    public void setDayID(String dayID) {
        this.dayID = dayID;
    }

    public int getSkierID() {
        return skierID;
    }

    public void setSkierID(int skierID) {
        this.skierID = skierID;
    }

    @Override
    public String toString() {
        return "ConsumerLiftRideDto [liftID=" + liftID + ", time=" + time;
    }
}

//// DTO for Response Message
//public static class ResponseMsg {
//    public String message;
//
//    public ResponseMsg(String message) {
//        this.message = message;
//    }
//}
