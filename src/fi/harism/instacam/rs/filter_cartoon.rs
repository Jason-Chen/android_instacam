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

void calculateBorders(rs_allocation allocation) {
	int width = rsAllocationGetDimX(allocation);
	int height = rsAllocationGetDimY(allocation);	
	
	const int borderWidth = 8;
	for (int x = borderWidth; x < width - borderWidth; ++x) {
		for (int y = borderWidth; y < height - borderWidth; ++y) {
			
			float3 sample[9];
			int i = 0;
			int tx = -borderWidth;
			for (int xx = 0; xx < 3; ++xx) {
				int ty = -borderWidth;
				for (int yy = 0; yy < 3; ++yy) {
					const uchar4* colorValue = rsGetElementAt(allocation, x + tx, y + ty);
					sample[i++] = rsUnpackColor8888(*colorValue).rgb;
					ty += borderWidth;
				}
				tx += borderWidth;
			}
			
			float3 horizEdge = sample[2] + sample[5] + sample[8] -
							(sample[0] + sample[3] + sample[6]);

			float3 vertEdge = sample[0] + sample[1] + sample[2] -
						(sample[6] + sample[7] + sample[8]);

			float3 border = sqrt((horizEdge * horizEdge) + 
								(vertEdge * vertEdge));

			float alpha = 1.0;
			if (border.r > 0.4 || border.g > 0.4 || border.b > 0.4){
				alpha = 1.0 - dot(border, border);
				alpha = alpha < 0.0 ? 0.0 : alpha;
			}
			
			uchar4* colorValue = (uchar4*)rsGetElementAt(allocation, x, y);
			float3 color = rsUnpackColor8888(*colorValue).rgb;			
			*colorValue = rsPackColorTo8888(color.r, color.g, color.b, alpha);
		}
	}
}

void root(uchar4* v_color) {
	float4 color = rsUnpackColor8888(*v_color);
	
	color.rgb *= color.a;
	color.a = 1.0;

	// Finally store color value back to allocation.
	color = clamp(color, 0.0f, 1.0f);
	*v_color = rsPackColorTo8888(color);
}
