//package Server;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import dto.LiftRideDto;
//
//import javax.servlet.ServletException;
//import javax.servlet.annotation.WebServlet;
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.regex.Pattern;
////import com.google.gson.Gson;
//import dto.ResponseMessage;
//
//@WebServlet(value = "/skiers/*" )  // handle url start with skiers
//public class SkierServlet extends HttpServlet {
//    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
//    private static final int INVALID_NUMERIC_PARAM = -11111;
////    private static final Gson gson = new Gson();
//    private static final ObjectMapper objectMapper = new ObjectMapper(); // 用 Jackson 代替 Gson
//    // EX: /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
//    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//
//        response.setContentType("application/json");
//        String urlPath = request.getPathInfo();
//
//        // check url is not empty
//        if(urlPath == null || urlPath.isEmpty()){
//            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST,"Invalid URL, missing paramterers");
//            return;
//        }
//
//        // url is not valid
//        String[] urlParts = urlPath.split("/");
//        System.out.println("URL Parts: " + Arrays.toString(urlParts) + " | Length: " + urlParts.length);
//
//        if(urlParts.length != 8) {
//            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST,"Invalid URL format");
//            return;
//        }
//        //parameters extract and validation
//        int resortID = validIntegerParam(urlParts[1],response, "resortID");
//        String seasonID = validStringParam(urlParts[3],response, "seasonID");
//        String dayID = validStringParam(urlParts[5],response, "dayID");
//        int skierID = validIntegerParam(urlParts[7],response, "skierID");
//
//        System.out.println("Extracted Params - ResortID: " + resortID +
//                ", SeasonID: " + seasonID +
//                ", DayID: " + dayID +
//                ", SkierID: " + skierID);
//
//        if (resortID == INVALID_NUMERIC_PARAM && seasonID == null && dayID == null && skierID == INVALID_NUMERIC_PARAM) {
//            return;
//        }
//
//        // parse request body
//        LiftRideDto liftRideDto = parseRequestBody(request, response);
//        if (liftRideDto == null) {
//            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid request body");
//            return;
//        }
//
//        // Dummy response
//        // return, request is valid
//        sendResponse(response, HttpServletResponse.SC_CREATED, "received request");
//    }
//
//    /**
//     * used to receive requestbody sent to servlet
//     * @return
//     */
//    private LiftRideDto parseRequestBody(HttpServletRequest request, HttpServletResponse response) throws IOException {
//        try(BufferedReader reader = request.getReader()) {
//            StringBuilder requestBody = new StringBuilder();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                requestBody.append(line);
//            }
//            System.out.println("Received JSON: " + requestBody); // Debugging log
//
//            LiftRideDto dto = objectMapper.readValue(requestBody.toString(), LiftRideDto.class);
//            System.out.println("Parsed DTO: " + dto.getLiftID() + ", " + dto.getTime()); // Debugging log
//            return dto;
//        } catch (Exception e) {
//            e.printStackTrace();
//            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "request body invalid");
//            return null;
//        }
//    }
//
//
//    /**
//     * check if param is a numeric value, return as int
//     * @param param
//     * @return
//     */
//    private int validIntegerParam(String param, HttpServletResponse response, String fieldname) throws IOException {
//        if (!NUMERIC_PATTERN.matcher(param).matches()){
//            // if param is not a numeric value
//            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, fieldname + " is not a valid numeric value");
//            return INVALID_NUMERIC_PARAM;
//        }
//        return Integer.parseInt(param);
//    }
//
//    /**
//     * check if param is a numeric value, return as string
//     * @param param
//     * @return
//     */
//    private String validStringParam(String param, HttpServletResponse response, String fieldname) throws IOException {
//        if (!NUMERIC_PATTERN.matcher(param).matches()){
//            // if param is not a numeric value to represent seasonid, dayid
//            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, fieldname + " is not a valid string value");
//            return null;
//        }
//        return param;
//    }
//
//    private void sendResponse(HttpServletResponse resp, int Code, String message) throws IOException {
//        resp.setStatus(Code);
//        // Create a response object
//        ResponseMessage responseMessage = new ResponseMessage(message);
//        // Convert the object to JSON
//        ObjectMapper objectMapper = new ObjectMapper();
//        String jsonResponse = objectMapper.writeValueAsString(responseMessage);
//        // Send JSON response
//        resp.getWriter().write(jsonResponse);
//    }
//}


package Server;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@WebServlet(value = "/skiers/*", asyncSupported = true) // 允许异步处理
public class SkierServlet extends HttpServlet {
    private static final int THREAD_POOL_SIZE = 200; // Adjust based on server capacity
    private static final ExecutorService requestExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);



    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 提交任务到线程池，而不是每次创建新线程
        // 启用 AsyncContext 避免主线程结束后 response 被回收
        AsyncContext asyncContext = request.startAsync();
        requestExecutor.submit(() -> {
            try {
                new SocketHandlerRunnable(asyncContext).run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void destroy() {
        // Graceful shutdown of thread pool when servlet is destroyed
        requestExecutor.shutdown();
        super.destroy();
    }
}

