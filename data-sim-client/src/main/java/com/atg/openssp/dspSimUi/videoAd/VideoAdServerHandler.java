package com.atg.openssp.dspSimUi.videoAd;

import com.atg.openssp.common.cache.dto.VideoAd;
import com.atg.openssp.common.provider.LoginHandler;
import com.atg.openssp.dspSimUi.model.ModelException;
import com.atg.openssp.dspSimUi.model.ad.video.VideoAdModel;
import com.atg.openssp.dspSimUi.model.client.VideoAdCommand;
import com.atg.openssp.dspSimUi.model.client.VideoAdCommandType;
import com.atg.openssp.dspSimUi.model.client.VideoAdResponse;
import com.atg.openssp.dspSimUi.model.client.ResponseStatus;
import com.google.gson.GsonBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Brian Sorensen
 */
public class VideoAdServerHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(VideoAdServerHandler.class);
    public static final String SITE_HOST = "videoAd-host";
    public static final String SITE_PORT = "videoAd-port";
    private final Thread t = new Thread(this);
    private final VideoAdModel model;
    private final GsonBuilder builder;
    private boolean running;

    public VideoAdServerHandler(VideoAdModel model) {
        this.model = model;
        t.setName("ServerHandler");
        t.setDaemon(true);

        builder = new GsonBuilder();
    }

    public void start() {
        if (!running) {
            t.start();
        }
    }

    @Override
    public void run() {
        running = true;
        while(running) {
            try {
                sendListCommand();
            } catch (ModelException e) {
                log.warn(e.getMessage(), e);
            }
            try {
                Thread.sleep(90000);
            } catch (InterruptedException e) {
                running = false;
            }
            Thread.yield();
        }
    }

    private void sendListCommand() throws ModelException {
        sendCommand(VideoAdCommandType.LIST);
    }

    public void sendAddCommand(VideoAd sb) throws ModelException {
        sendCommand(VideoAdCommandType.ADD, sb);
    }

    public void sendUpdateCommand(VideoAd sb) throws ModelException {
        sendCommand(VideoAdCommandType.UPDATE, sb);
    }

    public void sendRemoveCommand(String id) throws ModelException {
        VideoAd s = new VideoAd();
        s.setId(id);
        sendCommand(VideoAdCommandType.REMOVE, s);
    }

    public void sendLoadCommand() throws ModelException {
        sendCommand(VideoAdCommandType.LOAD);
    }

    public void sendImportCommand() throws ModelException {
        sendCommand(VideoAdCommandType.IMPORT);
    }

    public void sendExportCommand() throws ModelException {
        sendCommand(VideoAdCommandType.EXPORT);
    }

    public void sendClearCommand() throws ModelException {
        sendCommand(VideoAdCommandType.CLEAR);
    }

    private void sendCommand(VideoAdCommandType type) throws ModelException {
        sendCommand(type, null);
    }

    private void sendCommand(VideoAdCommandType type, VideoAd sb) throws ModelException {
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost("http://"+model.lookupProperty(SITE_HOST, "localhost")+":"+model.lookupProperty(SITE_PORT, "9090")+"/ssp-services/maintain/videoAds?t="+ LoginHandler.TOKEN);
            System.out.println(httpPost);
            VideoAdCommand command = new VideoAdCommand();
            command.setCommand(type);
            command.setVideoAd(sb);
            String jsonOut = builder.create().toJson(command);
            System.out.println(jsonOut);
            StringEntity entity = new StringEntity(jsonOut);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            CloseableHttpResponse response = client.execute(httpPost);
            if (response.getStatusLine().getStatusCode() == 200) {
                String json = EntityUtils.toString(response.getEntity(), "UTF-8");
                VideoAdResponse sr = builder.create().fromJson(json, VideoAdResponse.class);
                if (sr.getStatus() == ResponseStatus.SUCCESS) {
                    model.handleList(sr.getVideoAds());
                } else {
                    String m = type+" command failed with error: " + sr.getReason();
                    model.setMessageAsFault(m);
                    throw new ModelException(m);
                }
            } else {
                String m = type+" call failed with http error: " + response.getStatusLine().getStatusCode();
                model.setMessageAsFault(m);
                throw new ModelException(m);
            }
            client.close();
        } catch (IOException e) {
            model.setMessageAsFault("Could not access server: "+e.getMessage());
            throw new ModelException(e.getMessage());
        }
    }

}
