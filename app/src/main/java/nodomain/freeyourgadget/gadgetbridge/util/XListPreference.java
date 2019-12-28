/*  Copyright (C) 2019 krzys_h

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import nodomain.freeyourgadget.gadgetbridge.R;

public class XListPreference extends ListPreference {

    private final String CLASS_NAME = this.getClass().getSimpleName();
    private String dependentValue = "";

    public XListPreference(Context context) {
        this(context, null);
    }
    public XListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.XListPreference);
            dependentValue = a.getString(R.styleable.XListPreference_dependentValue);
            a.recycle();
        }
    }

    @Override
    public void setValue(String value) {
        String mOldValue = getValue();
        super.setValue(value);
        if (!value.equals(mOldValue)) {
            notifyDependencyChange(shouldDisableDependents());
        }
    }

    @Override
    public boolean shouldDisableDependents() {
        boolean shouldDisableDependents = super.shouldDisableDependents();
        String value = getValue();
        return shouldDisableDependents || value == null || !value.equals(dependentValue);
    }
}
