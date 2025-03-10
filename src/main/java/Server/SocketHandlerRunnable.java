package Server;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import dto.LiftRideDto;
import dto.ResponseMessage;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Pattern;

//AsyncContext

public class SocketHandlerRunnable implements Runnable {
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private static final int INVALID_NUMERIC_PARAM = -11111;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final AsyncContext asyncContext;
    private final BlockingQueue<Channel> channelPool;
    private static final String QUEUE_NAME = "skiQueue";



    public SocketHandlerRunnable(AsyncContext asyncContext, BlockingQueue<Channel> channelPool) {
        this.asyncContext = asyncContext;
        this.channelPool = channelPool;
    }

    @Override
    public void run() {
        HttpServletRequest request = (HttpServletRequest) asyncContext.getRequest();
        HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();

        try {
            // valid parameters
            processRequest(request, response);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            asyncContext.complete(); //  complete task, and release resource at end
        }
    }

    /**
     * verify the url and parameters valid
     * @param request
     * @param response
     * @throws IOException
     */
    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        String urlPath = request.getPathInfo();
        if (urlPath == null || urlPath.isEmpty()) {
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL, missing parameters");
            return;
        }

        String[] urlParts = urlPath.split("/");
        if (urlParts.length != 8) {
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid URL format");
            return;
        }

        // valid parameters
        int resortID = validIntegerParam(urlParts[1], "resortID", response);
        String seasonID = validStringParam(urlParts[3], "seasonID", response);
        String dayID = validStringParam(urlParts[5], "dayID", response);
        int skierID = validIntegerParam(urlParts[7], "skierID", response);

        if (resortID == INVALID_NUMERIC_PARAM || seasonID == null || dayID == null || skierID == INVALID_NUMERIC_PARAM) {
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid parameters: Check resortID, seasonID, dayID, skierID");
            return;
        }

        // parse request body data
        LiftRideDto liftRideDto = parseRequestBody(request, response);
        if (liftRideDto == null) {
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }

        // liftrideDTO
        liftRideDto.setResortID(resortID);
        liftRideDto.setSeasonID(seasonID);
        liftRideDto.setDayID(dayID);
        liftRideDto.setSkierID(skierID);

        // send to rabbitmq
        sendMessageToQueue(objectMapper.writeValueAsString(liftRideDto), response);

        // return 201 Created
        sendResponse(response, HttpServletResponse.SC_CREATED, "Received request");
    }

    /**
     * send message to rabbitmq
     * @param message
     * @param response
     * @throws IOException
     */
    private void sendMessageToQueue(String message, HttpServletResponse response) throws IOException {
        Channel channel = null;
        try {
            // get a Channel from pool
            channel = channelPool.take();

            // check if the queue exists
            AMQP.Queue.DeclareOk queueStatus = channel.queueDeclarePassive(QUEUE_NAME);
            int queueSize = queueStatus.getMessageCount();
            final int MAX_QUEUE_THRESHOLD = 1500; // max queue size

            // slow down if too many in the queue
            if (queueSize > MAX_QUEUE_THRESHOLD) {
                System.err.println("️ Queue size too high (" + queueSize + "), slowing down production...");
                sendResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is busy, try again later.");
                return;
            }

            // publish message
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println("Sent to queue: " + message);

        } catch (Exception e) {
            System.err.println(" IOException occurred: " + e.getMessage());
            sendResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "IOException occurred while sending message.");
        } finally {
            // return channel to pool
            if (channel != null) {
                if (channel.isOpen()) {
                    channelPool.offer(channel);
                } else {
                    System.err.println("Channel is closed, not returning to pool.");
                }
            }
        }
    }


    private LiftRideDto parseRequestBody(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
//            System.out.println("Received JSON: " + requestBody);
            LiftRideDto dto = objectMapper.readValue(requestBody.toString(), LiftRideDto.class);
//            System.out.println("Parsed DTO: " + dto.getLiftID() + ", " + dto.getTime());
            return dto;
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Request body invalid");
            return null;
        }
    }

    private int validIntegerParam(String param, String fieldname, HttpServletResponse response) throws IOException {
        if (!NUMERIC_PATTERN.matcher(param).matches()) {
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, fieldname + " is not a valid numeric value");
            return INVALID_NUMERIC_PARAM;
        }
        return Integer.parseInt(param);
    }

    private String validStringParam(String param, String fieldname, HttpServletResponse response) throws IOException {
        if (!NUMERIC_PATTERN.matcher(param).matches()) {
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, fieldname + " is not a valid string value");
            return null;
        }
        return param;
    }

    private void sendResponse(HttpServletResponse response, int code, String message) throws IOException {
        if (response.isCommitted()) {
            System.out.println("Response already committed, skipping: " + message);
            return;
        }
        response.setStatus(code);
        ResponseMessage responseMessage = new ResponseMessage(message);
        String jsonResponse = objectMapper.writeValueAsString(responseMessage);
        PrintWriter out = response.getWriter();
        out.write(jsonResponse);
        out.flush();
    }
}

