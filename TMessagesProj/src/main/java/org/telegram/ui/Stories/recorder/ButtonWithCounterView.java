package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

public class ButtonWithCounterView extends FrameLayout {

    private Theme.ResourcesProvider resourcesProvider;

    private final Paint paint;
    private final AnimatedTextView.AnimatedTextDrawable text;
    private final AnimatedTextView.AnimatedTextDrawable countText;
    private float countAlpha;
    private final AnimatedFloat countAlphaAnimated = new AnimatedFloat(350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final View rippleView;

    public ButtonWithCounterView(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, true, resourcesProvider);
    }

    public ButtonWithCounterView(Context context, boolean filled, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.resourcesProvider = resourcesProvider;

        ScaleStateListAnimator.apply(this, .02f, 1.2f);

        rippleView = new View(context);
        if (filled) {
            rippleView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 120)));
        } else {
            rippleView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 120)));
        }
        addView(rippleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (filled) {
            setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        }

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));

        text = new AnimatedTextView.AnimatedTextDrawable(true, true, false);
        text.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        text.setCallback(this);
        text.setTextSize(dp(14));
        //if (filled) {
            text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        //}
        text.setTextColor(Theme.getColor(filled ? Theme.key_featuredStickers_buttonText : Theme.key_featuredStickers_addButton, resourcesProvider));
        text.setGravity(Gravity.CENTER_HORIZONTAL);

        countText = new AnimatedTextView.AnimatedTextDrawable(false, false, true);
        countText.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        countText.setCallback(this);
        countText.setTextSize(dp(12));
        countText.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        countText.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        countText.setText("");
        countText.setGravity(Gravity.CENTER_HORIZONTAL);

        setWillNotDraw(false);
    }

    private boolean countFilled = true;
    public void setCountFilled(boolean filled) {
        countFilled = filled;
        countText.setTextSize(dp(countFilled ? 12 : 14));
        countText.setTextColor(
                countFilled ?
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) :
                text.getTextColor()
        );
    }

    private int timerSeconds = 0;
    private Runnable tick;
    public void setTimer(int seconds, Runnable whenTimerUp) {
        AndroidUtilities.cancelRunOnUIThread(tick);

        setCountFilled(false);
        setCount(timerSeconds = seconds, false);
        setShowZero(false);
        AndroidUtilities.runOnUIThread(tick = () -> {
            timerSeconds--;
            setCount(timerSeconds, true);
            if (timerSeconds > 0) {
                AndroidUtilities.runOnUIThread(tick, 1000);
            } else {
                setClickable(true);
                if (whenTimerUp != null) {
                    whenTimerUp.run();
                }
            }
        }, 1000);
    }
    public boolean isTimerActive() {
        return timerSeconds > 0;
    }

    public void setText(CharSequence newText, boolean animated) {
        if (animated) {
            text.cancelAnimation();
        }
        text.setText(newText, animated);
        setContentDescription(newText);
        invalidate();
    }

    private float loadingT = 0;
    private boolean loading;
    private ValueAnimator loadingAnimator;
    public void setLoading(boolean loading) {
        if (this.loading != loading) {
            if (loadingAnimator != null) {
                loadingAnimator.cancel();
                loadingAnimator = null;
            }

            loadingAnimator = ValueAnimator.ofFloat(loadingT, (this.loading = loading) ? 1 : 0);
            loadingAnimator.addUpdateListener(anm -> {
                loadingT = (float) anm.getAnimatedValue();
                invalidate();
            });
            loadingAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loadingT = loading ? 1 : 0;
                    invalidate();
                }
            });
            loadingAnimator.setDuration(320);
            loadingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            loadingAnimator.start();
        }
    }

    public boolean isLoading() {
        return loading;
    }

    private float countScale = 1;
    private ValueAnimator countAnimator;
    private void animateCount() {
        if (countAnimator != null) {
            countAnimator.cancel();
            countAnimator = null;
        }

        countAnimator = ValueAnimator.ofFloat(0, 1);
        countAnimator.addUpdateListener(anm -> {
            countScale = Math.max(1, (float) anm.getAnimatedValue());
            invalidate();
        });
        countAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                countScale = 1;
                invalidate();
            }
        });
        countAnimator.setInterpolator(new OvershootInterpolator(2.0f));
        countAnimator.setDuration(200);
        countAnimator.start();
    }

    private int lastCount;
    private boolean showZero;

    public void setShowZero(boolean showZero) {
        this.showZero = showZero;
    }

    public void setCount(int count, boolean animated) {
        if (animated) {
            countText.cancelAnimation();
        }
        if (animated && count != lastCount && count > 0 && lastCount > 0) {
            animateCount();
        }
        lastCount = count;
        countAlpha = count != 0 || showZero ? 1f : 0f;
        countText.setText("" + count, animated);
        invalidate();
    }

    public void setCount(String count, boolean animated) {
        if (animated) {
            countText.cancelAnimation();
            animateCount();
        }
        lastCount = -1;
        countAlpha = !TextUtils.isEmpty(count) || showZero ? 1f : 0f;
        countText.setText(count, animated);
        invalidate();
    }

    private float enabledT = 1;
    private boolean enabled = true;
    private ValueAnimator enabledAnimator;

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            if (enabledAnimator != null) {
                enabledAnimator.cancel();
                enabledAnimator = null;
            }

            enabledAnimator = ValueAnimator.ofFloat(enabledT, (this.enabled = enabled) ? 1 : 0);
            enabledAnimator.addUpdateListener(anm -> {
                enabledT = (float) anm.getAnimatedValue();
                invalidate();
            });
            enabledAnimator.start();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return text == who || countText == who || super.verifyDrawable(who);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return false;
    }

    private CircularProgressDrawable loadingDrawable;

    private int globalAlpha = 255;

    @Override
    protected void onDraw(Canvas canvas) {
        rippleView.draw(canvas);

        if (loadingT > 0) {
            if (loadingDrawable == null) {
                loadingDrawable = new CircularProgressDrawable(text.getTextColor());
            }
            int y = (int) ((1f - loadingT) * dp(24));
            loadingDrawable.setBounds(0, y, getWidth(), y + getHeight());
            loadingDrawable.setAlpha((int) (0xFF * loadingT));
            loadingDrawable.draw(canvas);
            invalidate();
        }

        if (loadingT < 1) {
            boolean restore = false;
            if (loadingT != 0) {
                canvas.save();
                canvas.translate(0, (int) (loadingT * dp(-24)));
                canvas.scale(1, 1f - .4f * loadingT);
                restore = true;
            }
            float textWidth = text.getCurrentWidth();
            float countAlpha = countAlphaAnimated.set(this.countAlpha);

            float width = textWidth + (dp(5.66f + 5 + 5) + countText.getCurrentWidth()) * countAlpha;
            AndroidUtilities.rectTmp2.set(
                    (int) ((getMeasuredWidth() - width - getWidth()) / 2f),
                    (int) ((getMeasuredHeight() - text.getHeight()) / 2f - dp(1)),
                    (int) ((getMeasuredWidth() - width + getWidth()) / 2f + textWidth),
                    (int) ((getMeasuredHeight() + text.getHeight()) / 2f - dp(1))
            );
            text.setAlpha((int) (globalAlpha * (1f - loadingT) * AndroidUtilities.lerp(.5f, 1f, enabledT)));
            text.setBounds(AndroidUtilities.rectTmp2);
            text.draw(canvas);

            AndroidUtilities.rectTmp2.set(
                    (int) ((getMeasuredWidth() - width) / 2f + textWidth + dp(countFilled ? 5 : 2)),
                    (int) ((getMeasuredHeight() - dp(18)) / 2f),
                    (int) ((getMeasuredWidth() - width) / 2f + textWidth + dp((countFilled ? 5 : 2) + 4 + 4) + Math.max(dp(9), countText.getCurrentWidth())),
                    (int) ((getMeasuredHeight() + dp(18)) / 2f)
            );
            AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);

            if (countScale != 1) {
                canvas.save();
                canvas.scale(countScale, countScale, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
            }
            if (countFilled) {
                paint.setAlpha((int) (globalAlpha * (1f - loadingT) * countAlpha * countAlpha * AndroidUtilities.lerp(.5f, 1f, enabledT)));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(10), dp(10), paint);
            }

            AndroidUtilities.rectTmp2.offset(-dp(.3f), -dp(.4f));
            countText.setAlpha((int) (globalAlpha * (1f - loadingT) * countAlpha * (countFilled ? 1 : .5f)));
            countText.setBounds(AndroidUtilities.rectTmp2);
            countText.draw(canvas);
            if (countScale != 1) {
                canvas.restore();
            }
            if (restore) {
                canvas.restore();
            }
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
//        info.setContentDescription(text.getText() + (lastCount > 0 ? ", " + LocaleController.formatPluralString("Chats", lastCount) : ""));
    }

    public void setTextAlpha(float v) {
        text.setAlpha((int) (v * 255));
    }

    public void setGlobalAlpha(float v) {
        globalAlpha = ((int) (v * 255));
    }
}
