Upic Ski Resort System 🚡🏔️

To run Client Version 1 (SkiersClient) or Client Version 2 (SkiersClient2), follow these steps:

#1. Update the EC2 Server IP Address
Before running the client, update the EC2 instance IP address in the corresponding client file.

Open SkiersClient.java or SkiersClient2.java
Locate the following variable:

private static final String SERVER_URL = "http://<your-ec2-ip>:8080/assignment1_war";

Replace <your-ec2-ip> with the actual EC2 public IP.

#2. Configure the Number of Events to Send
By default, the total number of events sent to the server is 200,000. If you need to modify this, update:
private static final int TOTAL_EVENTS = 200000;

#3. Troubleshooting
If the requests fail, verify the EC2 instance is running and the IP is correct.
Ensure the server is listening on port 8080.
If you experience high failure rates, consider reducing TOTAL_EVENTS
