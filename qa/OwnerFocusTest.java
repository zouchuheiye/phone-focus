package com.phonefocus.guard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class OwnerFocusTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void rejects(Runnable action,String label){boolean rejected=false;try{action.run();}catch(IllegalArgumentException|ArithmeticException expected){rejected=true;}check(rejected,label);}
    public static void main(String[] ignored){
        long wall=1_800_000_000_000L;
        FocusWindow window=FocusWindow.start(wall,20_000,7,25*60_000);
        check(window.remaining(wall,20_000,7)==1_500_000,"initial duration");
        check(window.remaining(wall+86_400_000,80_000,7)==1_440_000,"forward wall change cannot shorten same-boot deadline");
        check(window.remaining(wall-86_400_000,80_000,7)==1_440_000,"backward wall change cannot extend same-boot deadline");
        check(window.remaining(wall,1_520_000,7)==0,"exact deadline releases");
        check(window.remaining(wall,1_530_000,7)==0,"elapsed after deadline never negative");
        window=window.checkpoint(wall+60_000,80_000,7);
        check(window.savedRemaining==1_440_000,"checkpoint persists remaining");
        check(window.elapsedEnd==1_520_000,"checkpoint never shifts same-boot deadline");
        FocusWindow loaded=new FocusWindow(window.wallEnd,window.elapsedEnd,window.checkpointElapsed,window.savedRemaining,window.bootCount);
        check(loaded.remaining(wall+120_000,140_000,7)==1_380_000,"persistent round-trip");
        check(loaded.remaining(wall+300_000,1000,8)==1_200_000,"reboot accounts for wall-clock time passed");
        FocusWindow rebased=loaded.checkpoint(wall+300_000,1000,8);
        check(rebased.bootCount==8&&rebased.elapsedEnd==1_201_000,"reboot rebases to new elapsed clock");
        check(rebased.remaining(wall+50_000_000,61_000,8)==1_140_000,"new-boot monotonic clock takes over");
        check(loaded.remaining(wall-999_999_999,1000,8)==loaded.savedRemaining,"reboot clock rollback bounded by saved remainder");
        check(loaded.remaining(wall+1_500_000,1000,8)==0,"deadline passed during shutdown releases on boot");
        check(loaded.remaining(wall+300_000,0,7)==1_200_000,"elapsed counter regression uses bounded reboot fallback");
        FocusWindow expired=loaded.checkpoint(wall+1_500_000,1000,8);
        check(expired.remaining(wall-1_000_000,0,9)==0,"expired checkpoint never revives on rollback");
        rejects(()->FocusWindow.start(wall,0,0,0),"zero duration rejected");
        rejects(()->FocusWindow.start(wall,0,0,FocusWindow.MAX_DURATION_MS+1),"over 24h rejected");
        rejects(()->FocusWindow.start(Long.MAX_VALUE-5,0,0,1000),"deadline overflow rejected");
        rejects(()->FocusWindow.start(wall,0,-1,1000),"unknown boot identity rejected");

        Random random=new Random(20261022);
        for(int i=0;i<1000;i++){
            long duration=1000+random.nextInt(3_000_000),elapsed=100_000+random.nextInt(100_000);
            FocusWindow w=FocusWindow.start(wall,elapsed,42,duration);
            long spent=random.nextInt((int)duration+1);
            long unrelatedWall=wall+random.nextInt(2_000_000_000)-1_000_000_000L;
            check(w.remaining(unrelatedWall,elapsed+spent,42)==duration-spent,"monotonic random timing "+i);
            FocusWindow checkpoint=w.checkpoint(unrelatedWall,elapsed+spent,42);
            check(checkpoint.savedRemaining==duration-spent,"random checkpoint "+i);
            check(checkpoint.remaining(wall-99_999_999,0,43)<=checkpoint.savedRemaining,"reboot never extends saved bound "+i);
        }

        Set<String> codes=new HashSet<>();
        for(int i=0;i<32;i++){
            String code=RecoveryCode.generate(),digest=RecoveryCode.hash(code);
            check(codes.add(code),"recovery secrets unique");
            check(code.matches("[0-9A-F]{4}(-[0-9A-F]{4}){7}"),"human grouping");
            check(digest.matches("[0-9a-f]{64}"),"stored value is SHA256");
            check(RecoveryCode.matches("  "+code.toLowerCase().replace('-',' ') + "  ",digest),"spaces and case accepted");
            check(!RecoveryCode.matches(RecoveryCode.generate(),digest),"wrong secret denied");
            check(!RecoveryCode.matches(null,digest)&&!RecoveryCode.matches("",digest),"empty input denied");
            check(!RecoveryCode.matches(code,""),"missing digest cannot unlock");
        }
        rejects(()->RecoveryCode.hash("not a recovery code"),"invalid secret format rejected");

        // Each individual policy restore can fail. Others must still restore, and retry must finish.
        for(int failure=0;failure<7;failure++){
            int[] policies={9,9,9,9,9,9,9};boolean[] fault={true};int failing=failure;
            List<PolicyRollback.Step> steps=new ArrayList<>();List<Integer> attempted=new ArrayList<>();
            for(int i=0;i<7;i++){
                int index=i;
                steps.add(new PolicyRollback.Step("policy-"+i,()->{attempted.add(index);if(index==failing&&fault[0])throw new SecurityException("simulated");policies[index]=index;}));
            }
            List<String> errors=PolicyRollback.run(steps);
            check(errors.size()==1,"partial failure reported");
            check(attempted.size()==7,"all restores attempted despite failure");
            for(int i=0;i<7;i++)check(policies[i]==(i==failing?9:i),"unrelated policies restored");
            fault[0]=false;
            check(PolicyRollback.run(steps).isEmpty(),"retry clears failure");
            for(int i=0;i<7;i++)check(policies[i]==i,"retry completes original snapshot");
        }
        System.out.println("PASS: "+checks+" owner-focus checks; monotonic/reboot deadlines, emergency code verification, independent policy rollback/retry. No device-owner hardware claim.");
    }
}
