package app.ftl.extension.firefox;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.drawable.BitmapDrawable;

final class NoTintDrawable extends BitmapDrawable {

    NoTintDrawable(Resources resources, Bitmap bitmap) {
        super(resources, bitmap);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public void setTintList(ColorStateList tint) {
    }
}
