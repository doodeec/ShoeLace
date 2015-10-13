package com.doodeec.shoelace;

import android.support.annotation.IntDef;

import com.google.android.gms.wearable.DataEvent;

/**
 * Wrapper for wearable DataEvent attributes
 *
 * @author Dusan Bartos
 */
@IntDef({DataEvent.TYPE_CHANGED, DataEvent.TYPE_DELETED, MessageEventType.TYPE_MESSAGE})
public @interface EventType {
}
