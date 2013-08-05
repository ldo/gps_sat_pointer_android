package nz.gen.geek_central.GPSSatPointer;
/*
    Graphical display of directional arrows with labels.

    Copyright 2012, 2013 by Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.

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

import javax.microedition.khronos.opengles.GL10;
import nz.gen.geek_central.GLUseful.Vec3f;
import nz.gen.geek_central.GLUseful.Mat4f;
import nz.gen.geek_central.GLUseful.GLUseful;
import nz.gen.geek_central.GLUseful.GeomBuilder;
import nz.gen.geek_central.GLUseful.Lathe;
import nz.gen.geek_central.GLUseful.GLView;
import static nz.gen.geek_central.GLUseful.GLUseful.gl;
import nz.gen.geek_central.GraphicsUseful.PaintBuilder;

public class VectorView extends android.opengl.GLSurfaceView
  {
    public int Rotation;
      /* can't find any way to figure this out in this class, need an Activity to pass it to me */

  /* parameters for arrow: */
    private static final float BodyThickness = 0.05f;
    private static final float HeadThickness = 0.1f;
    private static final float HeadLengthOuter = 0.2f;
    private static final float HeadLengthInner = 0.1f;
    private static final float BaseBevel = 0.2f * BodyThickness;
    private static final int NrSectors = 12;

    private final int NullColor = getResources().getColor(R.color.nothing);
    private final GLUseful.Color CompassColor =
        new GLUseful.Color(getResources().getColor(R.color.compass));
    private final GLUseful.Color NormalSatColor =
        new GLUseful.Color(getResources().getColor(R.color.normal_sat));
    private final GLUseful.Color UsedSatColor =
        new GLUseful.Color(getResources().getColor(R.color.used_sat));
    private final GLUseful.Color FlashSatColor =
        new GLUseful.Color(getResources().getColor(R.color.flash_sat));

    private GLView Background;
    private GeomBuilder.Obj
        CompassArrow, SatArrow;
    private final java.util.HashMap<Integer, ArrowLabel>
        SatLabels = new java.util.HashMap<Integer, ArrowLabel>();
    private ArrowLabel CompassLabel;
    private final android.graphics.Paint LabelPaint = new PaintBuilder(true)
        .setTextSize(getResources().getDimension(R.dimen.label_text_size))
        .setColor(getResources().getColor(R.color.label_text))
        .get();

    private static class SatInfo
      {
      /* all the GpsSatellite info I care about */
        final float Azimuth, Elevation; /* radians */
        final int Prn; /* unique satellite id? Could use to colour-code or something */
        final boolean UsedInFix;

        public SatInfo
          (
            float Azimuth, /* degrees */
            float Elevation, /* degrees */
            int Prn,
            boolean UsedInFix
          )
          {
            this.Azimuth = (float)Math.toRadians(Azimuth);
            this.Elevation = (float)Math.toRadians(Elevation);
            this.Prn = Prn;
            this.UsedInFix = UsedInFix;
          } /*SatInfo*/

      } /*SatInfo*/;

    Mat4f ProjectionMatrix;
    float ViewRadius;

    private class ArrowLabel
      /* text labels positioned at arrow heads */
      {
        final GLView Image;
        boolean Used = true;

        public ArrowLabel
          (
            String Label
          )
          {
            final android.graphics.Rect TextBounds = new android.graphics.Rect();
            LabelPaint.getTextBounds(Label, 0, Label.length(), TextBounds);
            final float Slop = 0.2f;
            Image = new GLView
              (
                Math.round((TextBounds.right - TextBounds.left) * (1.0f + Slop)),
                Math.round((TextBounds.bottom - TextBounds.top) * (1.0f + Slop))
              );
              {
                final android.graphics.Canvas g = Image.Draw;
                g.drawColor(0, android.graphics.PorterDuff.Mode.SRC);
                  /* initialize all pixels to fully transparent */
                g.save();
                g.translate
                  (
                    (TextBounds.right - TextBounds.left) * Slop * 0.5f - TextBounds.left,
                    (TextBounds.bottom - TextBounds.top) * Slop * 0.5f - TextBounds.top
                  );
                g.drawText(Label, 0.0f, 0.0f, LabelPaint);
                g.restore();
              }
            Image.DrawChanged();
          } /*ArrowLabel*/

        public void Draw
          (
            Mat4f OrientMatrix
          )
          /* draws the label so its centre coincides with the tip of an arrow in the
            specified orientation. */
          {
            final Vec3f Where = ProjectionMatrix.mul(OrientMatrix).xform(new Vec3f(0.0f, 1.0f, 0.0f)).norm();
            Image.Draw
              (
                /*Projection =*/ Mat4f.identity(),
                /*Left =*/ Where.x - Image.BitsWidth / 2.0f / ViewRadius,
                /*Bottom =*/ Where.y - Image.BitsHeight / 2.0f / ViewRadius,
                /*Right =*/ Where.x + Image.BitsWidth / 2.0f / ViewRadius,
                /*Top =*/ Where.y + Image.BitsHeight / 2.0f / ViewRadius,
                /*Depth =*/ 0.01f
              );
          } /*Draw*/

        public void Release()
          /* frees up GL resources associated with this object. */
          {
            Image.Release();
          } /*Release*/

      } /*ArrowLabel*/;

    float OrientAzi = 0.0f;
      /* always Earth-horizontal, regardless of orientation of phone */
    float OrientElev = 0.0f;
      /* always around X-axis of phone, +ve is top-down, -ve is top-up */
    float OrientRoll = 0.0f;
      /* always around Y-axis of phone, +ve is anticlockwise
        viewed from bottom, -ve is clockwise, until it reaches
        ±90° when it its starts decreasing in magnitude again, so
        0° is when phone is horizontal either face-up or face-down */
    SatInfo[] Sats = {};
    int FlashPrn = -1;

    private static GeomBuilder.Obj MakeArrow
      (
        boolean Compass /* false for satellite */
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
        final Vec3f[] Points =
            new Vec3f[]
              {
                new Vec3f(0.0f, 1.0f, 0.0f),
                new Vec3f(HeadThickness, 1.0f - HeadLengthOuter, 0.0f),
                new Vec3f(BodyThickness, 1.0f - HeadLengthInner, 0.0f),
                new Vec3f(BodyThickness, Compass ? BaseBevel - 1.0f : BaseBevel, 0.0f),
                new Vec3f(BodyThickness - BaseBevel, Compass ? -0.98f : 0.02f, 0.0f),
                  /* y-coord of -1.0 seems to produce gaps in rendering when base
                    is face-on to viewer */
                new Vec3f(0.0f, Compass ? -1.0f : 0.0f, 0.0f),
              };
        final Vec3f[] Normals =
            new Vec3f[]
              {
                new Vec3f(OuterTiltSin, OuterTiltCos, 0.0f), /* tip */
                new Vec3f(InnerTiltSin, - InnerTiltCos, 0.0f), /* head */
                new Vec3f(1.0f, 0.0f, 0.0f), /* body */
                new Vec3f
                  (
                    (float)Math.sqrt(0.5f),
                    -(float)Math.sqrt(0.5f),
                    0.0f
                  ), /* bevel */
                new Vec3f(0.0f, -1.0f, 0.0f), /* base */
              };
        return
            Lathe.Make
              (
                /*Shaded =*/ true,
                /*Points =*/
                    new Lathe.VertexFunc()
                      {
                        public Vec3f Get
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
                        public Vec3f Get
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
                            final Vec3f OrigNormal =
                                Normals[PointIndex - (Upper ? 0 : 1)];
                            return
                                new Vec3f
                                  (
                                    OrigNormal.x * (float)Math.cos(FaceAngle),
                                    OrigNormal.y,
                                    OrigNormal.x * (float)Math.sin(FaceAngle)
                                  );
                          } /*Get*/
                      } /*VectorFunc*/,
                /*TexCoord = */ null,
                /*VertexColor =*/ null,
                /*NrSectors =*/ NrSectors,
                /*Uniforms =*/
                    new GeomBuilder.ShaderVarDef[]
                        {
                            new GeomBuilder.ShaderVarDef("arrow_color", GeomBuilder.ShaderVarTypes.COLOR3),
                        },
                /*VertexColorCalc =*/
                    "    vec3 light_direction = vec3(-0.7, 0.7, 0.0);\n" +
                    "    float light_brightness = 1.0;\n" +
                    "    float light_contrast = 0.5;\n" +
                    "    float attenuate = 1.2 - 0.4 * gl_Position.z;\n" +
                    "    frag_color = vec4\n" +
                    "      (\n" +
                    "            arrow_color\n" +
                    "        *\n" +
                    "            attenuate\n" +
                    "        *\n" +
                    "            (\n" +
                    "                light_brightness\n" +
                    "            -\n" +
                    "                light_contrast\n" +
                    "            +\n" +
                    "                    light_contrast\n" +
                    "                *\n" +
                    "                    dot\n" +
                    "                      (\n" +
                    "                        normalize(model_view * vec4(vertex_normal, 1.0)).xyz,\n" +
                    "                        normalize(light_direction)\n" +
                    "                      )\n" +
                    "            ),\n" +
                    "        1.0\n" +
                    "      );\n" +
                  /* simpleminded non-specular lighting */
                    "    back_color = vec4(vec3(0.5, 0.5, 0.5) * attenuate, 1.0);\n"
              );
      } /*MakeArrow*/

    public void SetOrientation
      (
        float[] Datum /* 3 values from orientation sensor */
      )
      /* sets reference orientation for computing satellite directions. */
      {
      /* work this out myself--SensorManager.getRotationMatrixFromVector
        isn't available before Android 2.3, API level 9 */
        OrientAzi = (float)Math.toRadians(Datum[0]);
        OrientElev = (float)Math.toRadians(Datum[1]);
        OrientRoll = (float)Math.toRadians(Datum[2]);
        requestRender();
      } /*SetOrientation*/

    public void SetSats
      (
        final Iterable<android.location.GpsSatellite> Sats
      )
      /* specifies a new set of satellite data to display. */
      {
        Render.QueueTask
          /* need to run on GL thread because of creation/deletion of ArrowLabel objects */
          (
            new Runnable()
              {
                public void run()
                  {
                    java.util.ArrayList<SatInfo> NewSats = new java.util.ArrayList<SatInfo>();
                    for (ArrowLabel ThisSat : SatLabels.values())
                      {
                        ThisSat.Used = false; /* initial assumption */
                      } /*for*/
                    for (android.location.GpsSatellite ThisSat : Sats)
                      {
                        final int ThisPrn = ThisSat.getPrn();
                        NewSats.add
                          (
                            new SatInfo
                              (
                                ThisSat.getAzimuth(),
                                ThisSat.getElevation(),
                                ThisPrn,
                                ThisSat.usedInFix()
                              )
                          );
                        final String ThisLabel = String.format(GLUseful.StdLocale, "%d", ThisPrn);
                        final ArrowLabel LabelEntry = SatLabels.get(ThisPrn);
                        if (LabelEntry != null)
                          {
                            LabelEntry.Used = true;
                          }
                        else
                          {
                            SatLabels.put(ThisPrn, new ArrowLabel(ThisLabel));
                          } /*if*/
                      } /*for*/
                    VectorView.this.Sats = NewSats.toArray(new SatInfo[NewSats.size()]);
                    final java.util.ArrayList<Integer> ToRemove = new java.util.ArrayList<Integer>();
                      /* cannot do concurrent removing while iterating */
                    for
                      (
                        java.util.Map.Entry<Integer, ArrowLabel> ThisSat : SatLabels.entrySet()
                      )
                      {
                        if (!ThisSat.getValue().Used)
                          {
                            ThisSat.getValue().Release();
                            ToRemove.add(ThisSat.getKey());
                          } /*if*/
                      } /*for*/
                    for (Integer ThisSat : ToRemove)
                      {
                        SatLabels.remove(ThisSat);
                      } /*for*/
                  } /*run*/
              } /*Runnable*/
          );
        requestRender();
      } /*SetSats*/

    public void SetFlashPrn
      (
        int NewFlashPrn /* -1 not to flash any sat */
      )
      {
        if (NewFlashPrn != FlashPrn)
          {
            FlashPrn = NewFlashPrn;
            requestRender();
          } /*if*/
      } /*SetFlashPrn*/

    public void Setup
      (
        int ViewWidth,
        int ViewHeight
      )
      /* initial setup for drawing that doesn't need to be done for every frame. */
      {
        ViewRadius = Math.min(ViewWidth, ViewHeight) / 2.0f;
        if (Background != null) /* force re-creation to match view dimensions */
          {
            Background.Release();
            Background = null;
          } /*if*/
        gl.glEnable(gl.GL_CULL_FACE);
        gl.glViewport
          (
            Math.round(ViewWidth / 2.0f - ViewRadius),
            Math.round(ViewHeight / 2.0f - ViewRadius),
            Math.round(2.0f * ViewRadius),
            Math.round(2.0f * ViewRadius)
          );
        ProjectionMatrix =
                Mat4f.frustum
                  (
                    /*L =*/ -0.1f,
                    /*R =*/ 0.1f,
                    /*B =*/ -0.1f,
                    /*T =*/ 0.1f,
                    /*N =*/ 0.1f,
                    /*F =*/ 3.1f
                  )
            .mul(
                Mat4f.translation(new Vec3f(0.0f, 0.0f, -1.6f))
            );
      } /*Setup*/

    public void Draw()
      /* draws all the satellite and compass arrows. Setup must already
        have been called on current GL context. */
      {
        if (CompassArrow == null)
          {
            CompassArrow = MakeArrow(true);
          } /*if*/
        if (SatArrow == null)
          {
            SatArrow = MakeArrow(false);
          } /*if*/
        if (CompassLabel == null)
          {
            CompassLabel = new ArrowLabel("N");
          } /*if*/
        if (Background == null)
          {
            final int ViewSize = Math.round(2.0f * ViewRadius);
            Background = new GLView(ViewSize, ViewSize);
            final android.graphics.Canvas g = Background.Draw;
            g.drawColor(NullColor, android.graphics.PorterDuff.Mode.SRC);
              /* initialize all pixels to fully transparent */
            g.save();
            g.translate(ViewRadius, ViewRadius);
            g.drawArc
              (
                /*oval =*/ new android.graphics.RectF(-ViewRadius, -ViewRadius, ViewRadius, ViewRadius),
                /*startAngle =*/ 0.0f,
                /*sweepAngle =*/ 360.0f,
                /*useCenter =*/ false,
                /*paint =*/ new PaintBuilder(true)
                    .setStyle(android.graphics.Paint.Style.FILL)
                    .setColor(getResources().getColor(R.color.background))
                    .get()
              );
            g.restore();
          } /*if*/
        GLUseful.ClearColor(new GLUseful.Color(NullColor));
        gl.glClear(gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(gl.GL_DEPTH_TEST);
        Background.Draw
          (
            /*Projection =*/ Mat4f.identity(),
            /*Left =*/ -1.0f,
            /*Bottom =*/ -1.0f,
            /*Right =*/ 1.0f,
            /*Top =*/ 1.0f,
            /*Depth =*/ 0.99f
          );
        final Mat4f Orientation =
            (
                Mat4f.rotation(Mat4f.AXIS_Y, OrientRoll)
            ).mul(
                Mat4f.rotation(Mat4f.AXIS_X, OrientElev)
            ).mul(
                Mat4f.rotation(Mat4f.AXIS_Z, OrientAzi)
            );
        gl.glEnable(gl.GL_DEPTH_TEST);
        CompassArrow.Draw
          (
            /*ProjectionMatrix =*/ ProjectionMatrix,
            /*ModelViewMatrix =*/ Orientation,
            /*Uniforms =*/
                new GeomBuilder.ShaderVarVal[]
                    {
                        new GeomBuilder.ShaderVarVal("arrow_color", CompassColor),
                    }
          );
        for (boolean DoingLabels = false;;)
          {
            for (SatInfo ThisSat : Sats)
              {
                final Mat4f SatDirection =
                        Orientation
                    .mul
                        (Mat4f.rotation(Mat4f.AXIS_Z, - ThisSat.Azimuth))
                    .mul
                        (Mat4f.rotation(Mat4f.AXIS_X, ThisSat.Elevation));
                if (DoingLabels)
                  {
                    SatLabels.get(ThisSat.Prn).Draw(SatDirection);
                  }
                else
                  {
                    SatArrow.Draw
                      (
                        /*ProjectionMatrix =*/ ProjectionMatrix,
                        /*ModelViewMatrix =*/ SatDirection,
                        /*Uniforms =*/
                            new GeomBuilder.ShaderVarVal[]
                                {
                                    new GeomBuilder.ShaderVarVal
                                      (
                                        "arrow_color",
                                        ThisSat.Prn == FlashPrn ?
                                            FlashSatColor
                                        : ThisSat.UsedInFix ?
                                            UsedSatColor
                                        :
                                            NormalSatColor
                                      ),
                                }
                      );
                  } /*if*/
              } /*for*/
            if (DoingLabels)
                break;
            gl.glDisable(gl.GL_DEPTH_TEST); /* all labels go on top */
            DoingLabels = true;
          } /*for*/
        CompassLabel.Draw(Orientation);
      } /*Draw*/

    private class VectorViewRenderer implements Renderer
      {
        private final java.util.ArrayList<Runnable> TaskQueue = new java.util.ArrayList<Runnable>();

        public void QueueTask
          (
            Runnable Task
          )
          /* queues a task to run on the GL thread, while a GL context is valid. */
          {
            synchronized (TaskQueue)
              {
                TaskQueue.add(Task);
              } /*synchronized*/
          } /*QueueTask*/

        public void onDrawFrame
          (
            GL10 _gl
          )
          {
            for (;;)
              {
                final Runnable Task;
                synchronized (TaskQueue)
                  {
                    Task = !TaskQueue.isEmpty() ? TaskQueue.remove(0) : null;
                  } /*synchronized*/
                if (Task == null)
                    break;
                Task.run();
              } /*for*/
            Draw();
          } /*onDrawFrame*/

        public void onSurfaceChanged
          (
            GL10 _gl,
            int ViewWidth,
            int ViewHeight
          )
          {
            Setup(ViewWidth, ViewHeight);
          } /*onSurfaceChanged*/

        public void onSurfaceCreated
          (
            GL10 _gl,
            javax.microedition.khronos.egl.EGLConfig Config
          )
          {
          /* leave all actual work to onSurfaceChanged */
          } /*onSurfaceCreated*/

      } /*VectorViewRenderer*/;

    final VectorViewRenderer Render = new VectorViewRenderer();

    public VectorView
      (
        android.content.Context TheContext,
        android.util.AttributeSet TheAttributes
      )
      {
        super(TheContext, TheAttributes);
        setEGLContextClientVersion(2);
        setRenderer(Render);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
      } /*VectorView*/

    @Override
    public void onPause()
      {
        super.onPause();
      /* losing the GL context anyway, so don't bother releasing anything: */
        CompassArrow = null;
        SatArrow = null;
        Background = null;
        CompassLabel = null;
        SatLabels.clear();
      } /*onPause*/

  } /*VectorView*/
