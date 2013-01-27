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
	
	int border = 6;
	for (int x = border; x < width - border; ++x)
	for (int y = border; y < height - border; ++y) {
		
		float pix[9];			
		for (int xx = 0; xx < 3; ++xx) {
			for (int yy = 0; yy < 3; ++yy) {
				const uchar4* colorValue = rsGetElementAt(src, x + (xx - 1) * border, y + (yy - 1) * border);
				pix[xx * 3 + yy] = length(rsUnpackColor8888(*colorValue).rgb);
			}
		}
		
		float delta = (
			fabs(pix[1]-pix[7]) +
			fabs(pix[5]-pix[3]) +
			fabs(pix[0]-pix[8]) +
			fabs(pix[2]-pix[6]) ) * 0.25;
		
		float3 color = { 0.8 * delta, 1.2 * delta, 2.0 * delta};
		
		uchar4* colorValue = (uchar4*)rsGetElementAt(dst, x, y);
		color = clamp(color, 0.0f, 1.0f);
		*colorValue = rsPackColorTo8888(color.r, color.g, color.b, 1.0f);
	}
}
