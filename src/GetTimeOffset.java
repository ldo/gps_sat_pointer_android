package nz.gen.geek_central.GPSSatPointer;
/*
    Respond to request for offset of system time from GPS time

    Copyright 2011 by Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
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
