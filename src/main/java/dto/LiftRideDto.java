package dto;

// DTO for LiftRide
public class LiftRideDto {
    private int liftID;
    private int time;

    // Default constructor (important for JSON serialization)
    public LiftRideDto() {}
    // Constructor with fields
    public LiftRideDto(Integer liftID, Integer time) {
        this.liftID = liftID;
        this.time = time;
    }
    // Getters and setters
    public Integer getLiftID() { return liftID; }
    public void setLiftID(Integer liftID) { this.liftID = liftID; }
    public Integer getTime() { return time; }
    public void setTime(Integer time) { this.time = time; }
}

//// DTO for Response Message
//public static class ResponseMsg {
//    public String message;
//
//    public ResponseMsg(String message) {
//        this.message = message;
//    }
//}
