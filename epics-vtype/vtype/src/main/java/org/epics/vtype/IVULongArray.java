/**
 * Copyright (C) 2010-14 diirt developers. See COPYRIGHT.TXT
 * All rights reserved. Use is subject to license terms. See LICENSE.TXT
 */
package org.epics.vtype;

import org.epics.util.array.ListInteger;
import org.epics.util.array.ListULong;

/**
 * Immutable {@code VULongArray} implementation.
 *
 * @author carcassi
 */
final class IVULongArray extends VULongArray {
    
    private final ListULong data;
    private final ListInteger sizes;
    private final Alarm alarm;
    private final Time time;
    private final Display display;

    IVULongArray(ListULong data, ListInteger sizes, Alarm alarm, Time time, Display display) {
        VType.argumentNotNull("data", data);
        VType.argumentNotNull("sizes", sizes);
        VType.argumentNotNull("alarm", alarm);
        VType.argumentNotNull("time", time);
        VType.argumentNotNull("display", display);
        this.data = data;
        this.alarm = alarm;
        this.time = time;
        this.display = display;
        this.sizes = sizes;
    }

    @Override
    public ListInteger getSizes() {
        return sizes;
    }

    @Override
    public ListULong getData() {
        return data;
    }

    @Override
    public Alarm getAlarm() {
        return alarm;
    }

    @Override
    public Time getTime() {
        return time;
    }

    @Override
    public Display getDisplay() {
        return display;
    }

}