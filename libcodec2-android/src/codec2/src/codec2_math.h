#ifndef CODEC2_MATH_H
#define CODEC2_MATH_H

//==========================================================================
// Name:            codec2_math.h
//
// Purpose:         A wrapper around architecture specific math libraries 
//                  used on embedded devices to improve Codec2 performance.
// Created:         May 15, 2022
// Authors:         Mooneer Salem
//
// License:
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU Lesser General Public License version 2.1,
//  as published by the Free Software Foundation.  This program is
//  distributed in the hope that it will be useful, but WITHOUT ANY
//  WARRANTY; without even the implied warranty of MERCHANTABILITY or
//  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
//  License for more details.
//
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program; if not, see <http://www.gnu.org/licenses/>.
//
//==========================================================================

#include <stddef.h>

#ifdef __cplusplus
extern "C"
{
#endif // __cplusplus

#include "comp.h"

//==========================================================================
// Note: the functions in this file must be implemented in your code if you 
// intend on using Codec2 in a non-ARM based embedded device. Otherwise, 
// linker errors will occur. For ARM, a default implementation of these 
// functions exists in codec2_math_arm.c.
//==========================================================================
    
//==========================================================================
/// Calculates the dot product of two real-valued float vectors.
///
/// @param leftHandSideRealVector A pointer to the first vector to use for the dot product.
/// @param rightHandSideRealVector A pointer to the second vector to use for the dot product.
/// @param vectorLength The length of the vector. Both vectors should be at least this long.
/// @param resultReal A pointer to the variable in which to store the scalar result.
///
//==========================================================================
void codec2_dot_product_f32(float* leftHandSideRealVector, float* rightHandSideRealVector, size_t vectorLength, float* resultReal);

//==========================================================================
/// Calculates the dot product of two complex-valued float vectors.
///
/// @param leftHandSideComplexVector A pointer to the first vector to use for the dot product.
/// @param rightHandSideComplexVector A pointer to the second vector to use for the dot product.
/// @param vectorLength The length of the vector. Both vectors should be at least this long.
/// @param resultReal A pointer to the variable in which to store the real component of the result.
/// @param resultImag A pointer to the variable in which to store the imaginary component of the result.
///
//==========================================================================
void codec2_complex_dot_product_f32(COMP* leftHandSideComplexVector, COMP* rightHandSideComplexVector, size_t vectorLength, float* resultReal, float* resultImag);

#ifdef __cplusplus
}
#endif // __cplusplus

#endif // CODEC2_MATH_H
