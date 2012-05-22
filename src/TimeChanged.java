package nz.gen.geek_central.GPSSatPointer;
/*
    Receive notification of changes to system time

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
