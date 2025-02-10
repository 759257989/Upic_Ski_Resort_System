package Server;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.LiftRideDto;
import dto.ResponseMessage;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.regex.Pattern;


public class SocketHandlerRunnable implements Runnable {
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private static final int INVALID_NUMERIC_PARAM = -11111;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AsyncContext asyncContext; //通过 AsyncContext 获取 request/response 进行处理。

    public SocketHandlerRunnable(AsyncContext asyncContext) {
        this.asyncContext = asyncContext;
    }

    @Override
    public void run() {
        // 从 AsyncContext 中获取 request 和 response
        HttpServletRequest request = (HttpServletRequest) asyncContext.getRequest();
        HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
        try {
            processRequest(request, response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

        // 提取并校验参数
        int resortID = validIntegerParam(urlParts[1], "resortID", response);
        String seasonID = validStringParam(urlParts[3], "seasonID", response);
        String dayID = validStringParam(urlParts[5], "dayID", response);
        int skierID = validIntegerParam(urlParts[7], "skierID", response);

        if (resortID == INVALID_NUMERIC_PARAM || seasonID == null || dayID == null || skierID == INVALID_NUMERIC_PARAM) {
            return;
        }

        // 解析请求体
        LiftRideDto liftRideDto = parseRequestBody(request, response);
        if (liftRideDto == null) {
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
            return;
        }

        //，返回 dummy 数据
        sendResponse(response, HttpServletResponse.SC_CREATED, "Received request");
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
        response.setStatus(code);
        ResponseMessage responseMessage = new ResponseMessage(message);
        String jsonResponse = objectMapper.writeValueAsString(responseMessage);
        PrintWriter out = response.getWriter();
        out.write(jsonResponse);
        out.flush();
    }
}

