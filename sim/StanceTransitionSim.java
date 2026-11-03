package cn.bfm;
public final class StanceTransitionSim {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Transition check "+checks);}
    private static boolean near(float a,float b){return Math.abs(a-b)<.00001f;}
    public static void main(String[] args){
        check(StanceTransition.duration(1.62f,true)==6);
        check(StanceTransition.duration(.4f,false)==7);
        for(boolean down:new boolean[]{true,false})for(int step=0;step<=100;step++){
            float start=.4f+1.22f*step/100;
            int ticks=StanceTransition.duration(start,down);
            float target=down?.4f:1.62f,previous=start;
            check(ticks>=2&&ticks<=(down?6:7));
            check(near(StanceTransition.height(start,down,0,ticks),start));
            check(near(StanceTransition.height(start,down,ticks,ticks),target));
            for(int i=0;i<=ticks*20;i++){
                float eye=StanceTransition.height(start,down,i/20f,ticks);
                check(eye>=.39999f&&eye<=1.62001f);
                check(down?eye<=previous+.00001f:eye>=previous-.00001f);
                float amount=StanceTransition.amount(eye);check(amount>=-.00001f&&amount<=1.00001f);
                // A reversal carries the exact current height instead of restarting from an endpoint.
                int reverse=StanceTransition.duration(eye,!down);
                check(near(StanceTransition.height(eye,!down,0,reverse),eye));
                previous=eye;
            }
        }
        float slideEye=.87f;
        check(near(StanceTransition.height(slideEye,true,0,3),slideEye));
        check(StanceTransition.duration(slideEye,true)<StanceTransition.ENTER_TICKS);
        check(StanceTransition.clampEye(Float.NaN)==StanceTransition.STAND_EYE);
        check(StanceTransition.amount(.4f)==1&&StanceTransition.amount(1.62f)==0);
        float a=(float)Math.toRadians(179),b=(float)Math.toRadians(-179);
        check(Math.abs(StanceTransition.angle(a,b,.5f)-a)<Math.toRadians(2));
        check(near(StanceTransition.angle(a,b,0),a));
        System.out.println("StanceTransitionSim: "+checks+" height/monotonic/reversal checks passed");
    }
}
