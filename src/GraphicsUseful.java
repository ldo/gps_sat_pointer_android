package nz.gen.geek_central.GPSSatPointer;
/*
    Useful 2D graphics stuff.

    Copyright 2012 by Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.

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

public class GraphicsUseful
  {
    public final static float Pi = (float)Math.PI;

    public static float ToRadians
      (
        float Degrees
      )
      {
        return Pi * Degrees / 180.0f;
      } /*ToRadians*/

    public static float ToDegrees
      (
        float Radians
      )
      {
        return 180.0f * Radians / Pi;
      } /*ToDegrees*/

  } /*GraphicsUseful*/
