package com.doodeec.shoelace;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dusan Bartos
 */
public class ShoeLace implements
        DataApi.DataListener,
        MessageApi.MessageListener,
        ResultCallback {

    private static final String TAG = ShoeLace.class.getSimpleName();
    /**
     * Request code for auto Google Play Services error resolution.
     */
    protected static final int REQUEST_CODE_RESOLUTION = 1;

    /**
     * Do not use this instance directly, always use thread-safe {@link #getInstance()} method
     */
    private static ShoeLace sInstance;

    /**
     * Gets singleton instance
     *
     * @return singleton
     */
    private static ShoeLace getInstance() {
        if (sInstance == null) {
            synchronized (ShoeLace.class) {
                if (sInstance == null) {
                    sInstance = new ShoeLace();
                }
            }
        }
        return sInstance;
    }

    public static void init(Context context) {
        ShoeLace instance = getInstance();
        if (instance.checkActive()) return;

        //TODO use new thread or stay in the same one?
        instance.initSelf(context);
    }

    public static void dismount() {
        getInstance().dismountSelf();
    }

    public static void tie(Object target) {
        Class<?> targetClass = target.getClass();
        ShoeLace instance = getInstance();
        // Process @Lace methods
        for (Method method : targetClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Lace.class)) {
                instance.saveInMap(target, method, method.getAnnotation(Lace.class));
            }
        }
    }

    public static void untie(Object target) {
        Class<?> targetClass = target.getClass();
        ShoeLace instance = getInstance();
        // Process @Lace methods
        for (Method method : targetClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Lace.class)) {
                instance.removeFromMap(target, method, method.getAnnotation(Lace.class));
            }
        }
    }

    public static void sendMessage(String message) {
        getInstance().sendMessageInternal(message);
    }

    public static void sendData(String dataKey, String serializedData) {
        getInstance().sendDataInternal(dataKey, serializedData);
    }

    private Map<String, List<TargetMethodWrapper>> mChangedMethodHandlerMap = new HashMap<>();
    private Map<String, List<TargetMethodWrapper>> mDeletedMethodHandlerMap = new HashMap<>();
    private Map<String, List<TargetMethodWrapper>> mMessageHandlerMap = new HashMap<>();
    /**
     * Context
     */
    private Context mContext;
    /**
     * Google API client.
     */
    private GoogleApiClient mGoogleApiClient;
    /**
     * Determines if the client is in a resolution state, and
     * waiting for resolution intent to return.
     */
    private boolean mIsInResolution;

    private ShoeLaceThread mThread;

    private void initSelf(Context context) {
        mContext = context;
        mThread = new ShoeLaceThread("shoeLaceThread");
        mThread.start();
        mThread.prepareHandler();

        Intent service = new Intent(context, WearableListener.class);
        context.startService(service);
/*
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();*/
    }

    private boolean checkActive() {
        return (mGoogleApiClient != null &&
                (mGoogleApiClient.isConnected() || mGoogleApiClient.isConnecting()));
    }

    @Override
    public void onResult(Result result) {
        if (!result.getStatus().isSuccess()) {
            Log.e(TAG, "Failed action " + result.getClass().getCanonicalName()
                    + " with status code: "
                    + result.getStatus().getStatusCode());
        } else {
            Log.d(TAG, "Action " + result.getClass().getCanonicalName()
                    + " successful");
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                processAssets(mChangedMethodHandlerMap, event);
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                processAssets(mDeletedMethodHandlerMap, event);
            } else {
                Log.w(TAG, "Unknown data event type = " + event.getType());
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        processMessage(messageEvent);
    }

    private void sendMessageInternal(final String message) {
        mThread.postTask(new Runnable() {
            @Override
            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    //noinspection unchecked
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), message, new byte[0])
                            .setResultCallback(ShoeLace.this);
                }
            }
        });
    }

    private void sendDataInternal(String dataKey, String serializedData) {
        Asset asset = Asset.createFromBytes(serializedData.getBytes());

        //TODO path
        PutDataMapRequest dataMap = PutDataMapRequest.create("/somepath");
        dataMap.getDataMap().putAsset(dataKey, asset);
        PutDataRequest request = dataMap.asPutDataRequest();
        Log.d(TAG, "Send data " + serializedData);
        //noinspection unchecked
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(this);
    }

    private void saveInMap(Object target, Method method, Lace lace) {
        Map<String, List<TargetMethodWrapper>> map = resolveMapType(lace);
        if (!map.containsKey(lace.value())) {
            map.put(lace.value(), new ArrayList<TargetMethodWrapper>());
        }
        map.get(lace.value()).add(new TargetMethodWrapper(target, method));
    }

    private void removeFromMap(Object target, Method method, Lace lace) {
        Map<String, List<TargetMethodWrapper>> map = resolveMapType(lace);
        if (map.containsKey(lace.value())) {
            map.get(lace.value()).remove(new TargetMethodWrapper(target, method));
        }
    }

    private Map<String, List<TargetMethodWrapper>> resolveMapType(Lace lace) {
        switch (lace.type()) {
            case DataEvent.TYPE_CHANGED:
                return mChangedMethodHandlerMap;

            case DataEvent.TYPE_DELETED:
                return mDeletedMethodHandlerMap;

            case MessageEventType.TYPE_MESSAGE:
                return mMessageHandlerMap;

            default:
                throw new IllegalStateException("Invalid event type");
        }
    }

    private void processAssets(Map<String, List<TargetMethodWrapper>> map, DataEvent event) {
        DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
        for (String mapKey : map.keySet()) {
            Asset asset = dataMapItem.getDataMap().getAsset(mapKey);

            if (asset != null) {
                for (TargetMethodWrapper wrapper : map.get(mapKey)) {
                    try {
                        wrapper.method.setAccessible(true);
                        wrapper.method.invoke(wrapper.target, asset);
                        Log.i(TAG, wrapper.method.getName());
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        Log.e(TAG, wrapper.method.getName(), ex.getCause());
                    }
                }
            }
        }
    }

    private void processMessage(MessageEvent messageEvent) {
        for (TargetMethodWrapper wrapper : mMessageHandlerMap.get(messageEvent.getPath())) {
            try {
                wrapper.method.setAccessible(true);
                wrapper.method.invoke(wrapper.target);
                Log.i(TAG, wrapper.method.getName());
            } catch (Throwable ex) {
                ex.printStackTrace();
                Log.e(TAG, wrapper.method.getName(), ex.getCause());
            }
        }
    }

    private void dismountSelf() {
        mChangedMethodHandlerMap.clear();
        mDeletedMethodHandlerMap.clear();
        mMessageHandlerMap.clear();
        mGoogleApiClient.disconnect();

        mContext.stopService(new Intent(mContext, WearableListener.class));
    }

    static class TargetMethodWrapper {
        Method method;
        Object target;

        TargetMethodWrapper(Object target, Method method) {
            this.target = target;
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (this == o) return true;
            if (!(o instanceof TargetMethodWrapper)) return false;

            TargetMethodWrapper that = (TargetMethodWrapper) o;
            return (this.target == that.target && this.method == that.method);
        }
    }

    /**
     * Should be basically singleton since it is only created from within ShoeLace singleton
     */
    public static class WearableListener extends WearableListenerService implements
            DataApi.DataListener,
            MessageApi.MessageListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        private static final String SER_TAG = WearableListener.class.getSimpleName();

        private ShoeLace instance = sInstance;

        @Override
        public void onCreate() {
            super.onCreate();
            Log.i(SER_TAG, "onCreate");

            instance.mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            instance.mGoogleApiClient.connect();
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.i(SER_TAG, "onConnected");
            Wearable.DataApi.addListener(instance.mGoogleApiClient, this);
            Wearable.MessageApi.addListener(instance.mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.i(SER_TAG, "onConnectionSuspended");
            retryConnecting();
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.i(SER_TAG, "onConnectionFailed");
            if (!connectionResult.hasResolution()) {
                // Show a localized error dialog.
                Log.e(SER_TAG, connectionResult.getErrorMessage());
                try {
                    GooglePlayServicesUtil.getErrorPendingIntent(connectionResult.getErrorCode(), getApplicationContext(), 0).send();
                } catch (PendingIntent.CanceledException e) {
                    retryConnecting();
                }
                return;
            }
            // If there is an existing resolution error being displayed or a resolution
            // activity has started before, do nothing and wait for resolution
            // progress to be completed.
            if (sInstance.mIsInResolution) {
                return;
            }
            sInstance.mIsInResolution = true;
            retryConnecting();
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.i(SER_TAG, "onDataChanged");
            sInstance.onDataChanged(dataEventBuffer);
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            Log.i(SER_TAG, "onMessageReceived");
            sInstance.onMessageReceived(messageEvent);
        }

        @Override
        public void onPeerConnected(Node peer) {
            super.onPeerConnected(peer);

        }

        @Override
        public void onPeerDisconnected(Node peer) {
            super.onPeerDisconnected(peer);
        }

        private void retryConnecting() {
            sInstance.mIsInResolution = false;
            if (!sInstance.mGoogleApiClient.isConnecting()) {
                sInstance.mGoogleApiClient.connect();
            }
        }
    }

    public class ShoeLaceThread extends HandlerThread {

        private Handler mWorkerHandler;

        public ShoeLaceThread(String name) {
            super(name);
        }

        public void postTask(Runnable task) {
            mWorkerHandler.post(task);
        }

        public void prepareHandler() {
            mWorkerHandler = new Handler(getLooper());
        }
    }
}
