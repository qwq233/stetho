package android.view;

import androidx.annotation.Nullable;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(View.class)
public class ViewHidden {
    @Nullable
    public ViewRootImpl getViewRootImpl() {
        throw new UnsupportedOperationException("STUB!");
    }
}
