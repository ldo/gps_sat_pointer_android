package nz.gen.geek_central.GPSTest;
/*
    Respond to request for offset of system time from GPS time
*/

public class GetTimeOffset extends android.content.BroadcastReceiver
  {

    @Override
    public void onReceive
      (
        android.content.Context Ctx,
        android.content.Intent What
      )
      {
        System.err.printf("GetTimeOffset received Intent %s\n", What.getAction()); /* debug */
      /* don't bother checking received Intent, because I only perform one action anyway */
      /* unfortunately BroadcastReceiver.PendingResult and goAsync are only available
        in API level 11 or later */
        if (isOrderedBroadcast())
          {
            if (Global.GotTimeDiscrepancy)
              {
                final android.os.Bundle Result = getResultExtras(true);
                Result.putDouble("offset_from_gps", Global.TimeDiscrepancy / 1000.0);
                setResultCode(android.app.Activity.RESULT_OK);
              }
            else
              {
                setResultCode(android.app.Activity.RESULT_FIRST_USER);
              } /*if*/
          }
        else
          {
            System.err.println("GetTimeOffset received unordered broadcast! Ignoring"); /* debug */
          } /*if*/
      } /*onReceive*/

  } /*GetTimeOffset*/
