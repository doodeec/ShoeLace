package com.doodeec.shoelace;

import com.google.android.gms.wearable.DataEvent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Lace
 *
 * Method is called on Worker Thread
 * {@literal @}Lace("DataPack") void onResultReceived(Asset receivedAsset)
 *
 * @author Dusan Bartos
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Lace {
    String value();

    @EventType int type() default DataEvent.TYPE_CHANGED;
}