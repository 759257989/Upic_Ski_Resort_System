package client.clientPART2;

public class LatencyRecord {
    private final long startTimeMillis; // When the request started (absolute time in ms)
    private final String requestType;   // e.g. "POST"
    private final long latencyMillis;   // Latency in ms
    private final int responseCode;     // HTTP response code

    public LatencyRecord(long startTimeMillis, String requestType, long latencyMillis, int responseCode) {
        this.startTimeMillis = startTimeMillis;
        this.requestType = requestType;
        this.latencyMillis = latencyMillis;
        this.responseCode = responseCode;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public String getRequestType() {
        return requestType;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }

    public int getResponseCode() {
        return responseCode;
    }

    // Returns a CSV-formatted line (raw values)
    @Override
    public String toString() {
        return startTimeMillis + "," + requestType + "," + latencyMillis + "," + responseCode;
    }
}
