package cn.bfm;

/** Shared camera/body progress, including reversal from a partially completed stance. */
public final class StanceTransition {
    public static final int ENTER_TICKS=8, EXIT_TICKS=9;
    /**
     * Ticks the slide camera takes to drop, shared by the client camera and the server's bullet origin.
     *
     * <p>Lives here rather than in the client so {@code SlideServer} can blend the same curve without
     * depending on a client-only class. Six matches the dive's entry pacing.
     */
    public static final int SLIDE_ENTER_TICKS=6;
    public static final float STAND_EYE=1.62F, PRONE_EYE=.4F;
    public static float clampEye(float value){return Float.isFinite(value)?Math.max(PRONE_EYE,Math.min(STAND_EYE,value)):STAND_EYE;}
    public static float smooth(float value){float x=Math.max(0,Math.min(1,value));return x*x*x*(x*(x*6-15)+10);}
    public static float height(float start,boolean down,float elapsed,int duration){
        float end=down?PRONE_EYE:STAND_EYE;
        return clampEye(start)+(end-clampEye(start))*smooth(duration<=0?1:elapsed/duration);
    }
    public static int duration(float start,boolean down){
        float distance=Math.abs((down?PRONE_EYE:STAND_EYE)-clampEye(start));
        return Math.max(2,(int)Math.ceil((down?ENTER_TICKS:EXIT_TICKS)*distance/(STAND_EYE-PRONE_EYE)));
    }
    public static float amount(float eye){return (STAND_EYE-clampEye(eye))/(STAND_EYE-PRONE_EYE);}
    public static float angle(float from,float to,float weight){
        float full=(float)(2*Math.PI),delta=(to-from)%full;
        if(delta>Math.PI)delta-=full;
        if(delta< -Math.PI)delta+=full;
        return from+delta*Math.max(0,Math.min(1,weight));
    }
    public static float blend(float start,float end,float elapsed,int duration){
        return start+(end-start)*smooth(duration<=0?1:elapsed/duration);
    }
    public static int fireDelayTicks(double seconds){
        return Double.isFinite(seconds)?(int)Math.ceil(Math.max(0,Math.min(10,seconds))*20):0;
    }
    private StanceTransition(){}
}
