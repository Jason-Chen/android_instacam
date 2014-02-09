/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

#pragma version(1)
#pragma rs java_package_name(fi.harism.instacam)

#include "utils.rsh"

void apply(const rs_allocation src, rs_allocation dst) {
	int width = rsAllocationGetDimX(src);
	int height = rsAllocationGetDimY(src);	
	
	const int borderWidth = 6;
	for (int x = borderWidth; x < width - borderWidth; ++x) {
		for (int y = borderWidth; y < height - borderWidth; ++y) {
			
			float3 color = { 0, 0, 0 };
			float3 sample[9];
			int i = 0;
			int tx = -borderWidth;
			for (int xx = 0; xx < 3; ++xx) {
				int ty = -borderWidth;
				for (int yy = 0; yy < 3; ++yy) {
					const uchar4* colorValue = rsGetElementAt(src, x + tx, y + ty);
					sample[i++] = rsUnpackColor8888(*colorValue).rgb;
					color += rsUnpackColor8888(*colorValue).rgb;
					ty += borderWidth;
				}
				tx += borderWidth;
			}
			color /= 9.0f;
			
			float3 horizEdge = sample[2] + sample[5] + sample[8] -
							(sample[0] + sample[3] + sample[6]);

			float3 vertEdge = sample[0] + sample[1] + sample[2] -
						(sample[6] + sample[7] + sample[8]);

			float3 border = sqrt((horizEdge * horizEdge) + 
								(vertEdge * vertEdge));

			float alpha = 1.0;
			if (border.r > 0.3 || border.g > 0.3 || border.b > 0.3){
				color *= 1.0f - dot(border, border);
			}
			
			const float3 colorRed = { 1.0, 0.3, 0.3 };
			const float3 colorGreen = { 0.3, 1.0, 0.3 };
			const float3 colorBlue =  { 0.3, 0.3, 1.0 };
		
			color = floor(color * 8.0f) * 0.125f;
			color = colorRed * color.r + colorBlue * color.b + colorGreen * color.g;
			
			uchar4* colorValue = (uchar4*)rsGetElementAt(dst, x, y);
			color = clamp(color, 0.0f, 1.0f);
			*colorValue = rsPackColorTo8888(color.r, color.g, color.b, 1.0f);
		}
	}
}
