/*
 * Copyright 2018 Rúben Sousa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rubensousa.gravitysnaphelper;


import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

class GravityDelegate {

    private OrientationHelper verticalHelper;
    private OrientationHelper horizontalHelper;
    private int gravity;
    private boolean isRtl;
    private boolean snapLastItem;
    private GravitySnapHelper.SnapListener listener;
    private int currentSnappedPosition;
    private boolean isScrolling = false;
    private boolean snapToPadding = false;
    private float scrollMsPerInch = 100f;
    private int maxScrollDistance = GravitySnapHelper.SCROLL_DISTANCE_DEFAULT;
    private float maxScrollDistanceOffset = 0f;
    private RecyclerView recyclerView;
    private RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (newState == RecyclerView.SCROLL_STATE_IDLE && listener != null) {
                if (currentSnappedPosition != RecyclerView.NO_POSITION && isScrolling) {
                    listener.onSnap(currentSnappedPosition);
                }
            }
            isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
        }
    };

    public GravityDelegate(int gravity, boolean enableSnapLast,
                           @Nullable GravitySnapHelper.SnapListener listener) {
        if (gravity != Gravity.START && gravity != Gravity.END
                && gravity != Gravity.BOTTOM && gravity != Gravity.TOP) {
            throw new IllegalArgumentException("Invalid gravity value. Use START " +
                    "| END | BOTTOM | TOP constants");
        }
        this.snapLastItem = enableSnapLast;
        this.gravity = gravity;
        this.listener = listener;
    }

    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (recyclerView != null) {
            recyclerView.setOnFlingListener(null);
            if (gravity == Gravity.START || gravity == Gravity.END) {
                isRtl = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault())
                        == ViewCompat.LAYOUT_DIRECTION_RTL;
            }
            if (listener != null) {
                recyclerView.addOnScrollListener(scrollListener);
            }
            this.recyclerView = recyclerView;
        } else {
            verticalHelper = null;
            horizontalHelper = null;
        }
    }

    public int getCurrentSnappedPosition() {
        return currentSnappedPosition;
    }

    public void setSnapToPadding(boolean ignorePadding) {
        this.snapToPadding = ignorePadding;
    }

    public void smoothScrollToPosition(int position) {
        scrollTo(position, true);
    }

    public void scrollToPosition(int position) {
        scrollTo(position, false);
    }

    public void setScrollMsPerInch(float ms) {
        scrollMsPerInch = ms;
    }

    public void setMaxScrollDistance(int distance) {
        maxScrollDistance = distance;
        maxScrollDistanceOffset = 0f;
    }

    public void setMaxScrollDistanceFromSize(float offset) {
        maxScrollDistanceOffset = offset;
    }

    @NonNull
    public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager layoutManager,
                                              @NonNull View targetView) {
        int[] out = new int[2];

        if (!(layoutManager instanceof LinearLayoutManager)) {
            return out;
        }

        LinearLayoutManager lm = (LinearLayoutManager) layoutManager;

        if (lm.canScrollHorizontally()) {
            if ((isRtl && gravity == Gravity.END) || (!isRtl && gravity == Gravity.START)) {
                out[0] = distanceToStart(targetView, getHorizontalHelper(lm));
            } else {
                out[0] = distanceToEnd(targetView, getHorizontalHelper(lm));
            }
        } else if (lm.canScrollVertically()) {
            if (gravity == Gravity.TOP) {
                out[1] = distanceToStart(targetView, getVerticalHelper(lm));
            } else { // BOTTOM
                out[1] = distanceToEnd(targetView, getVerticalHelper(lm));
            }
        }
        return out;
    }

    @Nullable
    public View findSnapView(RecyclerView.LayoutManager layoutManager) {
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return null;
        }
        View snapView = null;
        LinearLayoutManager lm = (LinearLayoutManager) layoutManager;
        switch (gravity) {
            case Gravity.START:
                snapView = findEdgeView(lm, getHorizontalHelper(lm), true);
                break;
            case Gravity.END:
                snapView = findEdgeView(lm, getHorizontalHelper(lm), false);
                break;
            case Gravity.TOP:
                snapView = findEdgeView(lm, getVerticalHelper(lm), true);
                break;
            case Gravity.BOTTOM:
                snapView = findEdgeView(lm, getVerticalHelper(lm), false);
                break;
        }
        if (snapView != null) {
            currentSnappedPosition = recyclerView.getChildAdapterPosition(snapView);
        } else {
            currentSnappedPosition = RecyclerView.NO_POSITION;
        }
        return snapView;
    }

    @NonNull
    public int[] calculateScrollDistance(int velocityX, int velocityY) {
        if (recyclerView == null
                || (verticalHelper == null && horizontalHelper == null)
                || (maxScrollDistance == -1 && maxScrollDistanceOffset == 0f)) {
            return new int[2];
        }
        final int[] out = new int[2];
        Scroller scroller = new Scroller(recyclerView.getContext(),
                new DecelerateInterpolator());
        int maxDistance = 0;
        if (maxScrollDistanceOffset != 0f) {
            if (verticalHelper != null) {
                maxDistance = (int) ((verticalHelper.getEndAfterPadding()
                        - verticalHelper.getStartAfterPadding()) * maxScrollDistanceOffset);
            } else if (horizontalHelper != null) {
                maxDistance = (int) ((horizontalHelper.getEndAfterPadding()
                        - horizontalHelper.getStartAfterPadding()) * maxScrollDistanceOffset);
            }
        } else {
            maxDistance = maxScrollDistance;
        }
        scroller.fling(0, 0, velocityX, velocityY,
                -maxDistance, maxDistance,
                -maxDistance, maxDistance);
        out[0] = scroller.getFinalX();
        out[1] = scroller.getFinalY();
        return out;
    }

    @Nullable
    public RecyclerView.SmoothScroller createScroller(RecyclerView.LayoutManager layoutManager) {
        if (!(layoutManager instanceof RecyclerView.SmoothScroller.ScrollVectorProvider)
                || recyclerView == null) {
            return null;
        }
        return new LinearSmoothScroller(recyclerView.getContext()) {
            @Override
            protected void onTargetFound(View targetView, RecyclerView.State state, RecyclerView.SmoothScroller.Action
                    action) {
                if (recyclerView == null || recyclerView.getLayoutManager() == null) {
                    // The associated RecyclerView has been removed so there is no action to take.
                    return;
                }
                int[] snapDistances = calculateDistanceToFinalSnap(recyclerView.getLayoutManager(),
                        targetView);
                final int dx = snapDistances[0];
                final int dy = snapDistances[1];
                final int time = calculateTimeForDeceleration(Math.max(Math.abs(dx), Math.abs(dy)));
                if (time > 0) {
                    action.update(dx, dy, time, mDecelerateInterpolator);
                }
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return scrollMsPerInch / displayMetrics.densityDpi;
            }
        };
    }

    public void enableLastItemSnap(boolean snap) {
        snapLastItem = snap;
    }

    private void scrollTo(int position, boolean smooth) {
        if (recyclerView.getLayoutManager() != null) {
            RecyclerView.ViewHolder viewHolder
                    = recyclerView.findViewHolderForAdapterPosition(position);
            if (viewHolder != null) {
                int[] distances = calculateDistanceToFinalSnap(recyclerView.getLayoutManager(),
                        viewHolder.itemView);
                if (smooth) {
                    recyclerView.smoothScrollBy(distances[0], distances[1]);
                } else {
                    recyclerView.scrollBy(distances[0], distances[1]);
                }
            } else {
                if (smooth) {
                    recyclerView.smoothScrollToPosition(position);
                } else {
                    recyclerView.scrollToPosition(position);
                }
            }
        }
    }

    private int distanceToStart(View targetView, @NonNull OrientationHelper helper) {
        int distance;
        // If we don't care about padding, just snap to the start of the view
        if (!snapToPadding) {
            int childStart = helper.getDecoratedStart(targetView);
            if (childStart >= helper.getStartAfterPadding() / 2) {
                distance = childStart - helper.getStartAfterPadding();
            } else {
                distance = childStart;
            }
        } else {
            distance = helper.getDecoratedStart(targetView) - helper.getStartAfterPadding();
        }
        return distance;
    }

    private int distanceToEnd(View targetView, @NonNull OrientationHelper helper) {
        int distance;

        if (!snapToPadding) {
            int childEnd = helper.getDecoratedEnd(targetView);
            if (childEnd >= helper.getEnd() - (helper.getEnd() - helper.getEndAfterPadding()) / 2) {
                distance = helper.getDecoratedEnd(targetView) - helper.getEnd();
            } else {
                distance = childEnd - helper.getEndAfterPadding();
            }
        } else {
            distance = helper.getDecoratedEnd(targetView) - helper.getEndAfterPadding();
        }

        return distance;
    }

    /**
     * Returns the first view that we should snap to.
     *
     * @param lm     the recyclerview's layout manager
     * @param helper orientation helper to calculate view sizes
     * @return the first view in the LayoutManager to snap to
     */
    @Nullable
    private View findEdgeView(LinearLayoutManager lm, OrientationHelper helper, boolean start) {
        if (lm.getChildCount() == 0) {
            return null;
        }

        // If we're at the end of the list, we shouldn't snap
        // to avoid having the last item not completely visible.
        if (isAtEndOfList(lm) && !snapLastItem) {
            return null;
        }

        View edgeView = null;
        int distanceToEdge = Integer.MAX_VALUE;

        for (int i = 0; i < lm.getChildCount(); i++) {
            View currentView = lm.getChildAt(i);
            int currentViewDistance;
            if ((start && !isRtl) || (!start && isRtl)) {
                currentViewDistance = Math.abs(helper.getDecoratedStart(currentView));
            } else {
                currentViewDistance = Math.abs(helper.getDecoratedEnd(currentView)
                        - helper.getEnd());
            }
            if (currentViewDistance < distanceToEdge) {
                distanceToEdge = currentViewDistance;
                edgeView = currentView;
            }
        }
        return edgeView;
    }

    private boolean isAtEndOfList(LinearLayoutManager lm) {
        if ((!lm.getReverseLayout() && gravity == Gravity.START)
                || (lm.getReverseLayout() && gravity == Gravity.END)
                || (!lm.getReverseLayout() && gravity == Gravity.TOP)
                || (lm.getReverseLayout() && gravity == Gravity.BOTTOM)) {
            return lm.findLastCompletelyVisibleItemPosition() == lm.getItemCount() - 1;
        } else {
            return lm.findFirstCompletelyVisibleItemPosition() == 0;
        }
    }

    OrientationHelper getVerticalHelper(RecyclerView.LayoutManager layoutManager) {
        if (verticalHelper == null) {
            verticalHelper = OrientationHelper.createVerticalHelper(layoutManager);
        }
        return verticalHelper;
    }

    OrientationHelper getHorizontalHelper(RecyclerView.LayoutManager layoutManager) {
        if (horizontalHelper == null) {
            horizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager);
        }
        return horizontalHelper;
    }
}
