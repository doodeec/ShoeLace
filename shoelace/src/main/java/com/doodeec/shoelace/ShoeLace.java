package com.doodeec.shoelace;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dusan Bartos
 */
public class ShoeLace implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        DataApi.DataListener {

    private static final String TAG = ShoeLace.class.getSimpleName();

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

    public static void init(final Context context) {
        ShoeLace instance = getInstance();
        if (instance.mGoogleApiClient != null &&
                (instance.mGoogleApiClient.isConnected() ||
                        instance.mGoogleApiClient.isConnecting())) {
            return;
        }

        //TODO use new thread or stay in the same one?
        new Thread(new Runnable() {
            @Override
            public void run() {
                ShoeLace instance = getInstance();
                instance.mGoogleApiClient = new GoogleApiClient.Builder(context)
                        .addConnectionCallbacks(instance)
                        .addOnConnectionFailedListener(instance)
                        .addApi(Wearable.API)
                        .build();
                instance.mGoogleApiClient.connect();
            }
        }).start();

    }

    public static void dismount() {
        ShoeLace instance = getInstance();
        instance.mChangedMethodHandlerMap.clear();
        instance.mDeletedMethodHandlerMap.clear();
        instance.mGoogleApiClient.disconnect();
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
                //TODO remove from map
            }
        }
    }

    private Map<String, List<TargetMethodWrapper>> mChangedMethodHandlerMap = new HashMap<>();
    private Map<String, List<TargetMethodWrapper>> mDeletedMethodHandlerMap = new HashMap<>();
    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "onConnected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "onConnectionSuspended");
        //TODO reconnect
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "onConnectionFailed");
        //TODO reconnect
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                processAssets(mChangedMethodHandlerMap, event);
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                processAssets(mDeletedMethodHandlerMap, event);
            } else {
                Log.d(TAG, "Unknown data event type = " + event.getType());
            }
        }
    }

    private void saveInMap(Object target, Method method, Lace lace) {
        Map<String, List<TargetMethodWrapper>> map;
        switch (lace.type()) {
            case DataEvent.TYPE_CHANGED:
                map = mChangedMethodHandlerMap;
                break;

            case DataEvent.TYPE_DELETED:
                map = mDeletedMethodHandlerMap;
                break;

            default:
                throw new IllegalStateException("Invalid event type");
        }

        if (!map.containsKey(lace.value())) {
            map.put(lace.value(), new ArrayList<TargetMethodWrapper>());
        }
        map.get(lace.value()).add(new TargetMethodWrapper(target, method));
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

    private static class TargetMethodWrapper {
        Method method;
        Object target;

        TargetMethodWrapper(Object target, Method method) {
            this.target = target;
            this.method = method;
        }
    }
}
