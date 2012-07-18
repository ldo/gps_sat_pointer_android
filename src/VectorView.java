package nz.gen.geek_central.GPSSatPointer;
/*
    Graphical display of directional arrows.

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

import android.util.FloatMath;
import nz.gen.geek_central.GLUseful.GeomBuilder;
import nz.gen.geek_central.GLUseful.Lathe;

public class VectorView
  {
    static final android.opengl.GLES11 gl = new android.opengl.GLES11(); /* for easier references */

  /* parameters for arrow: */
    private static final float BodyThickness = 0.05f;
    private static final float HeadThickness = 0.1f;
    private static final float HeadLengthOuter = 0.2f;
    private static final float HeadLengthInner = 0.1f;
    private static final float BaseBevel = 0.2f * BodyThickness;
    private static final int NrSectors = 12;

    private final GeomBuilder.Obj
        CompassArrow, SatArrow;

    static class SatInfo
      {
      /* all the GpsSatellite info I care about */
        final float Azimuth, Elevation; /* radians */
        final int Prn; /* unique satellite id? Could use to colour-code or something */

        public SatInfo
          (
            float Azimuth, /* degrees */
            float Elevation, /* degrees */
            int Prn
          )
          {
            this.Azimuth = GraphicsUseful.ToRadians(Azimuth);
            this.Elevation = GraphicsUseful.ToRadians(Elevation);
            this.Prn = Prn;
          } /*SatInfo*/

      } /*SatInfo*/

    float OrientAzi = 0.0f;
      /* always Earth-horizontal, regardless of orientation of phone */
    float OrientElev = 0.0f;
      /* always around X-axis of phone, +ve is top-down, -ve is top-up */
    float OrientRoll = 0.0f;
      /* always around Y-axis of phone, +ve is anticlockwise
        viewed from bottom, -ve is clockwise, until it reaches
        ±90° when it its starts decreasing in magnitude again, so
        0° is when phone is horizontal either face-up or face-down */
    Mat4f OrientMatrix = Mat4f.identity();
    SatInfo[] Sats = {};
    int FlashPrn = -1;

    private static GeomBuilder.Obj MakeArrow
      (
        boolean FullLength
      )
      {
        final float OuterTiltCos =
            HeadThickness / (float)Math.hypot(HeadThickness, HeadLengthOuter);
        final float OuterTiltSin =
            HeadLengthOuter / (float)Math.hypot(HeadThickness, HeadLengthOuter);
        final float InnerTiltCos =
            HeadThickness / (float)Math.hypot(HeadThickness, HeadLengthInner);
        final float InnerTiltSin =
            HeadLengthInner / (float)Math.hypot(HeadThickness, HeadLengthInner);
        final GeomBuilder.Vec3f[] Points =
            new GeomBuilder.Vec3f[]
              {
                new GeomBuilder.Vec3f(0.0f, 1.0f, 0.0f),
                new GeomBuilder.Vec3f(HeadThickness, 1.0f - HeadLengthOuter, 0.0f),
                new GeomBuilder.Vec3f(BodyThickness, 1.0f - HeadLengthInner, 0.0f),
                new GeomBuilder.Vec3f(BodyThickness, FullLength ? BaseBevel - 1.0f : BaseBevel, 0.0f),
                new GeomBuilder.Vec3f(BodyThickness - BaseBevel, FullLength ? -0.98f : 0.02f, 0.0f),
                  /* y-coord of -1.0 seems to produce gaps in rendering when base
                    is face-on to viewer */
                new GeomBuilder.Vec3f(0.0f, FullLength ? -1.0f : 0.0f, 0.0f),
              };
        final GeomBuilder.Vec3f[] Normals =
            new GeomBuilder.Vec3f[]
              {
                new GeomBuilder.Vec3f(OuterTiltSin, OuterTiltCos, 0.0f), /* tip */
                new GeomBuilder.Vec3f(InnerTiltSin, - InnerTiltCos, 0.0f), /* head */
                new GeomBuilder.Vec3f(1.0f, 0.0f, 0.0f), /* body */
                new GeomBuilder.Vec3f
                  (
                    FloatMath.sqrt(0.5f),
                    -FloatMath.sqrt(0.5f),
                    0.0f
                  ), /* bevel */
                new GeomBuilder.Vec3f(0.0f, -1.0f, 0.0f), /* base */
              };
        return
            Lathe.Make
              (
                /*Points =*/
                    new Lathe.VertexFunc()
                      {
                        public GeomBuilder.Vec3f Get
                          (
                            int PointIndex
                          )
                          {
                            return
                                Points[PointIndex];
                          } /*Get*/
                      } /*VertexFunc*/,
                /*NrPoints = */ Points.length,
                /*Normal =*/
                    new Lathe.VectorFunc()
                      {
                        public GeomBuilder.Vec3f Get
                          (
                            int PointIndex,
                            int SectorIndex, /* 0 .. NrSectors - 1 */
                            boolean Upper
                              /* indicates which of two calls for each point (except for
                                start and end points, which only get one call each) to allow
                                for discontiguous shading */
                          )
                          {
                            final float FaceAngle =
                                (float)(2.0 * Math.PI * SectorIndex / NrSectors);
                            final GeomBuilder.Vec3f OrigNormal =
                                Normals[PointIndex - (Upper ? 0 : 1)];
                            return
                                new GeomBuilder.Vec3f
                                  (
                                    OrigNormal.x * FloatMath.cos(FaceAngle),
                                    OrigNormal.y,
                                    OrigNormal.x * FloatMath.sin(FaceAngle)
                                  );
                          } /*Get*/
                      } /*VectorFunc*/,
                /*TexCoord = */ null,
                /*VertexColor =*/ null,
                /*NrSectors =*/ NrSectors
              );
      } /*MakeArrow*/

    public VectorView()
      {
        CompassArrow = MakeArrow(true);
        SatArrow = MakeArrow(false);
      } /*VectorView*/

    public void SetOrientation
      (
        float[] Datum /* 3 values from orientation sensor */
      )
      /* sets reference orientation for computing satellite directions. */
      {
      /* work this out myself--SensorManager.getRotationMatrixFromVector
        isn't available before Android 2.3, API level 9 */
        OrientAzi = GraphicsUseful.ToRadians(Datum[0]);
        OrientElev = GraphicsUseful.ToRadians(Datum[1]);
        OrientRoll = GraphicsUseful.ToRadians(Datum[2]);
        OrientMatrix =
                Mat4f.rotation(Mat4f.AXIS_Z, - OrientAzi)
            .mul(
                Mat4f.rotation(Mat4f.AXIS_X, - OrientElev)
            ).mul(
                Mat4f.rotation(Mat4f.AXIS_Y, OrientRoll)
            );
      } /*SetOrientation*/

    public void SetSats
      (
        Iterable<android.location.GpsSatellite> Sats
      )
      /* specifies a new set of satellite data to display. */
      {
        java.util.ArrayList<SatInfo> NewSats = new java.util.ArrayList<SatInfo>();
        for (android.location.GpsSatellite ThisSat : Sats)
          {
            NewSats.add
              (
                new SatInfo(ThisSat.getAzimuth(), ThisSat.getElevation(), ThisSat.getPrn())
              );
          } /*for*/
        this.Sats = NewSats.toArray(new SatInfo[NewSats.size()]);
      } /*SetSats*/

    public Vec3f OurDirection
      (
        float Azimuth,
        float Elevation
      )
     /* returns direction vector corresponding to Azimuth and Elevation in phone coordinates. */
      {
        Vec3f D;
        final float AziCos = FloatMath.cos(Azimuth);
        final float AziSin = FloatMath.sin(Azimuth);
        final float ElevCos = FloatMath.cos(Elevation);
        final float ElevSin = FloatMath.sin(Elevation);
        D = OrientMatrix.xform
          (
            new Vec3f
              (
                  AziSin * ElevCos,
                  - AziCos * ElevCos,
                  ElevSin
              )
          );
        return 
            D;
      } /*OurDirection*/

    public android.graphics.PointF PointAt
      (
        float Azimuth,
        float Elevation,
        float Radius
      )
      /* returns the coordinates where the tip of the arrow should lie. */
      {
        final Vec3f D = OurDirection(Azimuth, Elevation);
        final float[] Result = {0.0f, + Radius};
        final android.graphics.Matrix Orient = new android.graphics.Matrix();
        Orient.postScale(1.0f, (float)Math.hypot(D.x, D.y));
          /* perspective foreshortening factor */
        Orient.postRotate(GraphicsUseful.ToDegrees((float)Math.atan2(- D.x, D.y)));
        Orient.mapPoints(Result);
        return
            new android.graphics.PointF(Result[0], Result[1]);
      } /*PointAt*/

    public void Setup
      (
        int ViewWidth,
        int ViewHeight
      )
      /* initial setup for drawing that doesn't need to be done for every frame. */
      {
        gl.glEnable(gl.GL_CULL_FACE);
      /* gl.glEnable(gl.GL_MULTISAMPLE); */ /* doesn't seem to make any difference */
        gl.glShadeModel(gl.GL_SMOOTH);
        gl.glEnable(gl.GL_LIGHTING);
        gl.glEnable(gl.GL_LIGHT0);
        gl.glEnable(gl.GL_DEPTH_TEST);
        gl.glViewport(0, 0, ViewWidth, ViewHeight);
        gl.glMatrixMode(gl.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glFrustumf
          (
            /*l =*/ - (float)ViewWidth / ViewHeight,
            /*r =*/ (float)ViewWidth / ViewHeight,
            /*b =*/ -1.0f,
            /*t =*/ 1.0f,
            /*n =*/ 1.0f,
            /*f =*/ 10.0f
          );
      } /*Setup*/

    public void Draw()
      /* draws all the satellite and compass arrows. Setup must already
        have been called on current GL context. */
      {
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClear(gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT);
        gl.glMatrixMode(gl.GL_MODELVIEW);
        gl.glLoadIdentity();
      /* Note that, by positioning the light _before_ doing all the
        rotate calls, its position is fixed relative to the display,
        not the arrows. */
        gl.glLightfv
          (
            /*light =*/ gl.GL_LIGHT0,
            /*pname =*/ gl.GL_POSITION,
            /*params =*/ new float[] {0.0f, 2.0f, -2.0f, 1.0f},
            /*offset =*/ 0
          );
        gl.glLightfv
          (
            /*light =*/ gl.GL_LIGHT0,
            /*pname =*/ gl.GL_AMBIENT,
            /*params =*/ new float[] {0.4f, 0.4f, 0.4f, 1.0f},
            /*offset =*/ 0
          );
        gl.glLightfv
          (
            /*light =*/ gl.GL_LIGHT0,
            /*pname =*/ gl.GL_SPECULAR,
            /*params =*/ new float[] {0.7f, 0.7f, 0.7f, 1.0f},
            /*offset =*/ 0
          );
        gl.glMaterialfv
          (
            /*face =*/ gl.GL_FRONT_AND_BACK,
            /*pname =*/ gl.GL_AMBIENT,
            /*params =*/ new float[] {0.4f, 0.4f, 0.4f, 1.0f},
            /*offset =*/ 0
          );
        gl.glTranslatef(0, 0, -3.0f);
        gl.glScalef(2.0f, 2.0f, 2.0f);
        gl.glFrontFace(gl.GL_CCW);
        gl.glRotatef(GraphicsUseful.ToDegrees(OrientRoll), 0, 1, 0);
        gl.glRotatef(GraphicsUseful.ToDegrees(OrientElev), 1, 0, 0);
        gl.glRotatef(GraphicsUseful.ToDegrees(OrientAzi), 0, 0, 1);
        for (SatInfo ThisSat : Sats)
          {
            gl.glPushMatrix();
            gl.glRotatef(- GraphicsUseful.ToDegrees(ThisSat.Azimuth), 0, 0, 1);
            gl.glRotatef(GraphicsUseful.ToDegrees(ThisSat.Elevation), 1, 0, 0);
            gl.glMaterialfv
              (
                /*face =*/ gl.GL_FRONT_AND_BACK,
                /*pname =*/ gl.GL_SPECULAR,
                /*params =*/
                    ThisSat.Prn == FlashPrn ?
                        new float[] {0.81f, 0.08f, 0.93f, 1.0f}
                    :
                        new float[] {0.93f, 0.87f, 0.04f, 1.0f},
                /*offset =*/ 0
              );
            SatArrow.Draw();
            gl.glPopMatrix();
          } /*for*/
        gl.glMaterialfv
          (
            /*face =*/ gl.GL_FRONT_AND_BACK,
            /*pname =*/ gl.GL_SPECULAR,
            /*params =*/ new float[] {0.28f, 0.76f, 0.69f, 1.0f},
            /*offset =*/ 0
          );
        CompassArrow.Draw();
      } /*Draw*/

  } /*VectorView*/
