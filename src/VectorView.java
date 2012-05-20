package nz.gen.geek_central.GPSTest;
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
import android.opengl.GLES11;
import nz.gen.geek_central.GLUseful.GeomBuilder;
import nz.gen.geek_central.GLUseful.Lathe;

class GraphicsUseful
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

    public static android.graphics.Paint FillWithColor
      (
        int TheColor
      )
      /* returns a Paint that will fill with a solid colour. */
      {
        final android.graphics.Paint ThePaint = new android.graphics.Paint();
        ThePaint.setStyle(android.graphics.Paint.Style.FILL);
        ThePaint.setColor(TheColor);
        ThePaint.setAntiAlias(true);
        return
            ThePaint;
      } /*FillWithColor*/

    public static void DrawCenteredText
      (
        android.graphics.Canvas Draw,
        String TheText,
        android.graphics.PointF Where,
        android.graphics.Paint UsePaint
      )
      /* draws text at position x, vertically centred around y. */
      {
        final android.graphics.Rect TextBounds = new android.graphics.Rect();
        UsePaint.getTextBounds(TheText, 0, TheText.length(), TextBounds);
        Draw.drawText
          (
            TheText,
            Where.x, /* depend on UsePaint to align horizontally */
            Where.y - (TextBounds.bottom + TextBounds.top) / 2.0f,
            UsePaint
          );
      } /*DrawCenteredText*/

  } /*GraphicsUseful*/

public class VectorView
  {
  /* parameters for arrow: */
    private static final float BodyThickness = 0.05f;
    private static final float HeadThickness = 0.1f;
    private static final float HeadLengthOuter = 0.4f;
    private static final float HeadLengthInner = 0.2f;
    private static final float BaseBevel = 0.2f * BodyThickness;
    private static final int NrSectors = 12;

    private final GeomBuilder.Obj ArrowShape;

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

    public VectorView()
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
                new GeomBuilder.Vec3f(BodyThickness, BaseBevel - 1.0f, 0.0f),
                new GeomBuilder.Vec3f(BodyThickness - BaseBevel, -0.98f, 0.0f),
                  /* y-coord of -1.0 seems to produce gaps in rendering when base
                    is face-on to viewer */
                new GeomBuilder.Vec3f(0.0f, -1.0f, 0.0f),
              };
        final GeomBuilder.Vec3f[] Normals =
            new GeomBuilder.Vec3f[]
              {
                new GeomBuilder.Vec3f(OuterTiltSin, OuterTiltCos, 0.0f), /* tip */
                new GeomBuilder.Vec3f(InnerTiltSin, - InnerTiltCos, 0.0f), /* head */
                new GeomBuilder.Vec3f(1.0f, 0.0f, 0.0f), /* body */
                new GeomBuilder.Vec3f
                  (
                    android.util.FloatMath.sqrt(0.5f),
                    -android.util.FloatMath.sqrt(0.5f),
                    0.0f
                  ), /* bevel */
                new GeomBuilder.Vec3f(0.0f, -1.0f, 0.0f), /* base */
              };
        ArrowShape = Lathe.Make
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
                                OrigNormal.x * android.util.FloatMath.cos(FaceAngle),
                                OrigNormal.y,
                                OrigNormal.x * android.util.FloatMath.sin(FaceAngle)
                              );
                      } /*Get*/
                  } /*VectorFunc*/,
            /*TexCoord = */ null,
            /*VertexColor =*/ null,
            /*NrSectors =*/ NrSectors
          );
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
      /* returns the coordinates where PointTo places the tip of the arrow. */
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
        GLES11.glEnable(GLES11.GL_CULL_FACE);
      /* GLES11.glEnable(GLES11.GL_MULTISAMPLE); */ /* doesn't seem to make any difference */
        GLES11.glShadeModel(GLES11.GL_SMOOTH);
        GLES11.glEnable(GLES11.GL_LIGHTING);
        GLES11.glEnable(GLES11.GL_LIGHT0);
        GLES11.glEnable(GLES11.GL_DEPTH_TEST);
        GLES11.glViewport(0, 0, ViewWidth, ViewHeight);
        GLES11.glMatrixMode(GLES11.GL_PROJECTION);
        GLES11.glLoadIdentity();
        GLES11.glFrustumf
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
        GLES11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES11.glClear(GLES11.GL_COLOR_BUFFER_BIT | GLES11.GL_DEPTH_BUFFER_BIT);
        GLES11.glMatrixMode(GLES11.GL_MODELVIEW);
        GLES11.glLoadIdentity();
      /* Note that, by positioning the light _before_ doing all the
        rotate calls, its position is fixed relative to the display,
        not the arrows. */
        GLES11.glLightfv
          (
            /*light =*/ GLES11.GL_LIGHT0,
            /*pname =*/ GLES11.GL_POSITION,
            /*params =*/ new float[] {0.0f, 2.0f, -2.0f, 1.0f},
            /*offset =*/ 0
          );
        GLES11.glLightfv
          (
            /*light =*/ GLES11.GL_LIGHT0,
            /*pname =*/ GLES11.GL_AMBIENT,
            /*params =*/ new float[] {0.4f, 0.4f, 0.4f, 1.0f},
            /*offset =*/ 0
          );
        GLES11.glLightfv
          (
            /*light =*/ GLES11.GL_LIGHT0,
            /*pname =*/ GLES11.GL_SPECULAR,
            /*params =*/ new float[] {0.7f, 0.7f, 0.7f, 1.0f},
            /*offset =*/ 0
          );
        GLES11.glMaterialfv
          (
            /*face =*/ GLES11.GL_FRONT_AND_BACK,
            /*pname =*/ GLES11.GL_AMBIENT,
            /*params =*/ new float[] {0.4f, 0.4f, 0.4f, 1.0f},
            /*offset =*/ 0
          );
        GLES11.glTranslatef(0, 0, -3.0f);
        GLES11.glScalef(2.0f, 2.0f, 2.0f);
        GLES11.glFrontFace(GLES11.GL_CCW);
        GLES11.glRotatef(GraphicsUseful.ToDegrees(OrientRoll), 0, 1, 0);
        GLES11.glRotatef(GraphicsUseful.ToDegrees(OrientElev), 1, 0, 0);
        GLES11.glRotatef(GraphicsUseful.ToDegrees(OrientAzi), 0, 0, 1);
        for (SatInfo ThisSat : Sats)
          {
            GLES11.glPushMatrix();
            GLES11.glRotatef(- GraphicsUseful.ToDegrees(ThisSat.Azimuth), 0, 0, 1);
            GLES11.glRotatef(GraphicsUseful.ToDegrees(ThisSat.Elevation), 1, 0, 0);
            GLES11.glMaterialfv
              (
                /*face =*/ GLES11.GL_FRONT_AND_BACK,
                /*pname =*/ GLES11.GL_SPECULAR,
                /*params =*/
                    ThisSat.Prn == FlashPrn ?
                        new float[] {0.81f, 0.82f, 0.93f, 1.0f}
                    :
                        new float[] {0.93f, 0.87f, 0.04f, 1.0f},
                /*offset =*/ 0
              );
            ArrowShape.Draw(); /* TBD old shape was half length of compass arrow */
            GLES11.glPopMatrix();
          } /*for*/
        GLES11.glMaterialfv
          (
            /*face =*/ GLES11.GL_FRONT_AND_BACK,
            /*pname =*/ GLES11.GL_SPECULAR,
            /*params =*/ new float[] {0.28f, 0.76f, 0.69f, 1.0f},
            /*offset =*/ 0
          );
        ArrowShape.Draw();
      } /*Draw*/

  } /*VectorView*/
