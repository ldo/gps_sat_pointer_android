package nz.gen.geek_central.GPSTest;
/*
    Receive notification of changes to system time
*/

public class TimeChanged extends android.content.BroadcastReceiver
  {

    @Override
    public void onReceive
      (
        android.content.Context Ctx,
        android.content.Intent What
      )
      {
        System.err.printf("TimeChanged received Intent %s\n", What.getAction()); /* debug */
        Global.GotTimeDiscrepancy = false;
      } /*onReceive*/

  } /*TimeChanged*/
