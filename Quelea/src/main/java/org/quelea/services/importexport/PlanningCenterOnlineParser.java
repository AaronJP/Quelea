/*
 * This file is part of Quelea, free projection software for churches.
 * 
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.quelea.services.importexport;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.ProgressBar;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.quelea.services.utils.LoggerUtils;
import org.quelea.services.utils.QueleaProperties;

/**
 *
 * @author Bronson
 */
public class PlanningCenterOnlineParser {

    private static final Logger LOGGER = LoggerUtils.getLogger();
    private final HttpClient httpClient;
    private final BasicCookieStore cookieStore;
    private final HttpContext httpContext;

    public PlanningCenterOnlineParser() {

        // need to handle cookies
        java.net.CookieManager cm = new java.net.CookieManager(null, CookiePolicy.ACCEPT_ALL);
        java.net.CookieHandler.setDefault(cm);

        httpClient = HttpClients.createDefault();
//        httpClient.setRedirectHandler(new DefaultRedirectHandler() {
//            @Override
//            public boolean isRedirectRequested(HttpResponse response, HttpContext context) {
//                boolean isRedirect = super.isRedirectRequested(response, context);
//                if (!isRedirect) {
//                    int responseCode = response.getStatusLine().getStatusCode();
//                    if (responseCode == 301 || responseCode == 302) {
//                        return true;
//                    }
//                }
//                return isRedirect;
//            }
//        });
        cookieStore = new BasicCookieStore();
        httpContext = new BasicHttpContext();
        httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
    }

    public boolean login(String email, String password) {
        LOGGER.log(Level.INFO, "Logging in");
        String url = "https://accounts.planningcenteronline.com/login";
        String result = post(url, email, password);
        LOGGER.log(Level.INFO, "Login result: {0}", result);
        if (result == null) {
            return false;
        }
        return !result.contains("<title>Login - Accounts</title>");
    }

    private String post(String urlString, String email, String password) {
        try {
            HttpPost httpost = new HttpPost(urlString);
            List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("email", email));
            nvps.add(new BasicNameValuePair("password", password));
            httpost.setEntity(new UrlEncodedFormEntity(nvps));

            HttpResponse response = httpClient.execute(httpost, httpContext);

            LOGGER.log(Level.INFO, "Response code {0}", response.getStatusLine().getStatusCode());
            for (Header header : response.getAllHeaders()) {
                LOGGER.log(Level.INFO, "Response header ({0}:{1})", new Object[]{header.getName(), header.getValue()});
            }

            Header[] cookieList = response.getHeaders("Set-Cookie");
            for (Header cookieHeader : cookieList) {
                String cookieStr = cookieHeader.getValue();

                HttpCookie httpCookie = HttpCookie.parse(cookieStr).get(0);
                BasicClientCookie cookie = new BasicClientCookie(httpCookie.getName(), httpCookie.getValue());
                cookie.setPath(httpCookie.getPath());
                cookie.setDomain(httpCookie.getDomain());

                Date today = new Date();
                Date tomorrow = new Date(today.getTime() + (1000 * 60 * 60 * 24));
                cookie.setExpiryDate(tomorrow);
                cookieStore.addCookie(cookie);
            }

            HttpEntity entity = response.getEntity();
            String text = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
            return text;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error", e);
        }

        return null;
    }

    private String get(String urlString) {
        try {
            HttpGet httget = new HttpGet(urlString);
            HttpResponse response = httpClient.execute(httget, httpContext);

            HttpEntity entity = response.getEntity();
            String text = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
            return text;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error", e);
        }

        return null;
    }

    private JSONObject getJson(String urlString) {
        try {
            String jsonStr = get(urlString);

            // fix up arrays JSONParser wont handle
            if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                jsonStr = "{\"array\":" + jsonStr + "}";
            }

            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(jsonStr);
            return json;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error", e);
        }

        return null;
    }

    // Contains organisation data - service types
    public JSONObject organisation() {
        return getJson("https://services.planningcenteronline.com/organization.json");
    }

    // Contains all plans for a certain service type
    public JSONObject serviceTypePlans(Long serviceTypeId) {
        return getJson("https://planningcenteronline.com/service_types/" + serviceTypeId + "/plans.json");
    }

    // Plan data
    public JSONObject plan(Long planId) {
        return getJson("https://planningcenteronline.com/plans/" + planId + ".json?include_slides=true");
    }

    // Arrangement data
    public JSONObject arrangement(Long arrangementId) {
        return getJson("https://planningcenteronline.com/arrangements/" + arrangementId + ".json");
    }

    // Media data
    public JSONObject media(Long mediaId) {
        return getJson("https://services.planningcenteronline.com/medias/" + mediaId + ".json");
    }

    // Download file from url to fileName, putting the file into the download directory
    // if the file exists it wont be downloaded
    // will give the file a temporary name until the download is fully complete at
    // which point it will rename to indicate the file is downloaded properly
    public String downloadFile(String url, String fileName, ProgressBar progressBar, Date lastUpdated) {
        try {
            QueleaProperties props = QueleaProperties.get();
            String fullFileName = FilenameUtils.concat(props.getDownloadPath(), fileName);
            File file = new File(fullFileName);
            if (file.exists()) {
                long lastModified = file.lastModified();
                if (lastUpdated == null || lastUpdated.getTime() <= lastModified) {
                    return file.getAbsolutePath();
                }

                // file is going to get overridden as it failed the timestamp check
                if (!file.delete()) {
                    // deletion of exiting file failed! just use the existing file then
                    System.out.println("Couldn't delete existing file: " + fullFileName);
                    return file.getAbsolutePath();
                }
            }

            String partFullFileName = fullFileName + ".part";
            File partFile = new File(partFullFileName);

            HttpGet httpget = new HttpGet(url);
            HttpResponse response = httpClient.execute(httpget, httpContext);
            HttpEntity entity = response.getEntity();
            if (entity != null) {

                long contentLength = entity.getContentLength();

                InputStream is = entity.getContent();
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(partFile))) {
                    Long totalBytesRead = 0L;
                    
                    byte buffer[] = new byte[1024 * 1024];
                    int count;
                    while ((count = is.read(buffer)) != -1) {
                        bos.write(buffer, 0, count);
                        
                        totalBytesRead += count;
                        progressBar.setProgress((double) totalBytesRead / (double) contentLength);
                    }
                }

                EntityUtils.consume(entity);
            }

            boolean success = partFile.renameTo(file);
            if (success && lastUpdated != null) {
                file.setLastModified(lastUpdated.getTime()); // set file timestamp to same as on PCO
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error", e);
        }

        return "";
    }
}
