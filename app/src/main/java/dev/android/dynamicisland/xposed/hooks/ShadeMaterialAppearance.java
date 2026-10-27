package dev.android.dynamicisland.xposed.hooks;

import android.view.View;
import dev.android.dynamicisland.xposed.Refl;
import java.util.Map;
import java.util.WeakHashMap;

/** Hide the stock shell only after our own shell paints; restore it when the last owner leaves. */
final class ShadeMaterialAppearance {
    private static final Map<View,State> ROWS=new WeakHashMap<>();
    private View owner,row;
    void sync(View host,boolean painted) {
        View next=painted ? MediaRowBackground.findRow(host) : null;
        if(row!=next){clear();row=next;owner=host;}
        if(row==null)return;
        State state=ROWS.computeIfAbsent(row,State::new);state.owners.put(host,Boolean.TRUE);state.hide();
    }
    void clear() {
        if(row!=null) {
            State state=ROWS.get(row);
            if(state!=null){state.owners.remove(owner);if(state.owners.isEmpty()){state.restore();ROWS.remove(row);}}
        }
        row=null;owner=null;
    }
    private static final class State {
        final View row;final boolean clip;
        final Map<View,Boolean> owners=new WeakHashMap<>();
        final Map<View,Float> backgrounds=new WeakHashMap<>();
        State(View row){this.row=row;clip=row.getClipToOutline();}
        void hide() {
            for(String name:new String[]{"mBackgroundNormal","mBackgroundDimmed"}) {
                if(Refl.field(row.getClass(),name)==null)continue;
                Object value=Refl.get(row,name);
                if(value instanceof View) {
                    View background=(View)value;
                    if(!backgrounds.containsKey(background)||background.getAlpha()!=0)backgrounds.put(background,background.getAlpha());
                    background.setAlpha(0);
                }
            }
            row.setClipToOutline(false);
        }
        void restore() {
            for(Map.Entry<View,Float> entry:backgrounds.entrySet())if(entry.getKey().getAlpha()==0)entry.getKey().setAlpha(entry.getValue());
            if(!row.getClipToOutline())row.setClipToOutline(clip);
        }
    }
}
