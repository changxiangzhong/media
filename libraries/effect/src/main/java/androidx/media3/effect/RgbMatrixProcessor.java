/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.effect;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Pair;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import java.io.IOException;

/** Applies an {@link RgbMatrix} to each frame. */
/* package */ final class RgbMatrixProcessor extends SingleFrameGlTextureProcessor {
  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH =
      "shaders/fragment_shader_transformation_es2.glsl";

  private final GlProgram glProgram;
  private final RgbMatrix rgbMatrix;

  // TODO(b/239431666): Support chaining multiple RgbMatrix instances in RgbMatrixProcessor.
  // TODO(b/239757183): Merge RgbMatrixProcessor with MatrixTransformationProcessor.
  public RgbMatrixProcessor(Context context, RgbMatrix rgbMatrix, boolean useHdr)
      throws FrameProcessingException {
    super(useHdr);
    this.rgbMatrix = rgbMatrix;

    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }

    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

    float[] identityMatrix = new float[16];
    Matrix.setIdentityM(identityMatrix, /* smOffset= */ 0);
    glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return Pair.create(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    float[] rgbMatrixArray = rgbMatrix.getMatrix(presentationTimeUs);
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.setFloatsUniform("uColorMatrix", rgbMatrixArray);
      glProgram.bindAttributesAndUniforms();

      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }
  }
}