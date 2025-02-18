/* Copyright 2002-2022 CS GROUP
 * Licensed to CS GROUP (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.utils;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.Field;
import org.hipparchus.analysis.differentiation.DSFactory;
import org.hipparchus.analysis.differentiation.DerivativeStructure;
import org.hipparchus.analysis.differentiation.FieldDerivativeStructure;
import org.hipparchus.analysis.differentiation.FieldUnivariateDerivative1;
import org.hipparchus.analysis.differentiation.FieldUnivariateDerivative2;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.random.RandomGenerator;
import org.hipparchus.random.Well1024a;
import org.hipparchus.util.Decimal64;
import org.hipparchus.util.Decimal64Field;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.time.FieldTimeStamped;
import org.orekit.time.TimeScalesFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TimeStampedFieldAngularCoordinatesTest {

    @Test
    public void testZeroRate() {
        TimeStampedFieldAngularCoordinates<DerivativeStructure> angularCoordinates =
                new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH,
                                                         createRotation(0.48, 0.64, 0.36, 0.48, false),
                                                         createVector(0, 0, 0, 4),
                                                         createVector(0, 0, 0, 4));
        Assertions.assertEquals(createVector(0, 0, 0, 4), angularCoordinates.getRotationRate());
        double dt = 10.0;
        TimeStampedFieldAngularCoordinates<DerivativeStructure> shifted = angularCoordinates.shiftedBy(dt);
        Assertions.assertEquals(0.0, shifted.getRotationAcceleration().getNorm().getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, shifted.getRotationRate().getNorm().getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, FieldRotation.distance(angularCoordinates.getRotation(), shifted.getRotation()).getReal(), 1.0e-15);
    }

    @Test
    public void testShift() {
        double rate = 2 * FastMath.PI / (12 * 60);
        TimeStampedFieldAngularCoordinates<DerivativeStructure> angularCoordinates =
                new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH,
                                                         createRotation(1, 0, 0, 0, false),
                                                         new FieldVector3D<>(rate, createVector(0, 0, 1, 4)),
                                                         createVector(0, 0, 0, 4));
        Assertions.assertEquals(rate, angularCoordinates.getRotationRate().getNorm().getReal(), 1.0e-10);
        double dt = 10.0;
        double alpha = rate * dt;
        TimeStampedFieldAngularCoordinates<DerivativeStructure> shifted = angularCoordinates.shiftedBy(dt);
        Assertions.assertEquals(rate, shifted.getRotationRate().getNorm().getReal(), 1.0e-10);
        Assertions.assertEquals(alpha, FieldRotation.distance(angularCoordinates.getRotation(), shifted.getRotation()).getReal(), 1.0e-10);

        FieldVector3D<DerivativeStructure> xSat = shifted.getRotation().applyInverseTo(createVector(1, 0, 0, 4));
        Assertions.assertEquals(0.0, xSat.subtract(createVector(FastMath.cos(alpha), FastMath.sin(alpha), 0, 4)).getNorm().getReal(), 1.0e-10);
        FieldVector3D<DerivativeStructure> ySat = shifted.getRotation().applyInverseTo(createVector(0, 1, 0, 4));
        Assertions.assertEquals(0.0, ySat.subtract(createVector(-FastMath.sin(alpha), FastMath.cos(alpha), 0, 4)).getNorm().getReal(), 1.0e-10);
        FieldVector3D<DerivativeStructure> zSat = shifted.getRotation().applyInverseTo(createVector(0, 0, 1, 4));
        Assertions.assertEquals(0.0, zSat.subtract(createVector(0, 0, 1, 4)).getNorm().getReal(), 1.0e-10);

    }

    @Test
    public void testToAC() {
        Random random = new Random(0xc9b4cf6c371108e0l);
        for (int i = 0; i < 100; ++i) {
            FieldRotation<DerivativeStructure> r = randomRotation(random);
            FieldVector3D<DerivativeStructure> o = randomVector(random, 1.0e-3);
            FieldVector3D<DerivativeStructure> a = randomVector(random, 1.0e-3);
            TimeStampedFieldAngularCoordinates<DerivativeStructure> acds =
        new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH, r, o, a);
            AngularCoordinates ac = acds.toAngularCoordinates();
            Assertions.assertEquals(0, Rotation.distance(r.toRotation(), ac.getRotation()), 1.0e-15);
            Assertions.assertEquals(0, FieldVector3D.distance(o, ac.getRotationRate()).getReal(), 1.0e-15);
            Assertions.assertEquals(0, FieldVector3D.distance(a, ac.getRotationAcceleration()).getReal(), 1.0e-15);
        }
    }

    @Test
    public void testSpin() {
        double rate = 2 * FastMath.PI / (12 * 60);
        TimeStampedFieldAngularCoordinates<DerivativeStructure> angularCoordinates =
                new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH,
                                                         createRotation(0.48, 0.64, 0.36, 0.48, false),
                                                         new FieldVector3D<>(rate, createVector(0, 0, 1, 4)),
                                        createVector(0, 0, 0, 4));
        Assertions.assertEquals(rate, angularCoordinates.getRotationRate().getNorm().getReal(), 1.0e-10);
        double dt = 10.0;
        TimeStampedFieldAngularCoordinates<DerivativeStructure> shifted = angularCoordinates.shiftedBy(dt);
        Assertions.assertEquals(rate, shifted.getRotationRate().getNorm().getReal(), 1.0e-10);
        Assertions.assertEquals(rate * dt, FieldRotation.distance(angularCoordinates.getRotation(), shifted.getRotation()).getReal(), 1.0e-10);

        FieldVector3D<DerivativeStructure> shiftedX  = shifted.getRotation().applyInverseTo(createVector(1, 0, 0, 4));
        FieldVector3D<DerivativeStructure> shiftedY  = shifted.getRotation().applyInverseTo(createVector(0, 1, 0, 4));
        FieldVector3D<DerivativeStructure> shiftedZ  = shifted.getRotation().applyInverseTo(createVector(0, 0, 1, 4));
        FieldVector3D<DerivativeStructure> originalX = angularCoordinates.getRotation().applyInverseTo(createVector(1, 0, 0, 4));
        FieldVector3D<DerivativeStructure> originalY = angularCoordinates.getRotation().applyInverseTo(createVector(0, 1, 0, 4));
        FieldVector3D<DerivativeStructure> originalZ = angularCoordinates.getRotation().applyInverseTo(createVector(0, 0, 1, 4));
        Assertions.assertEquals( FastMath.cos(rate * dt), FieldVector3D.dotProduct(shiftedX, originalX).getReal(), 1.0e-10);
        Assertions.assertEquals( FastMath.sin(rate * dt), FieldVector3D.dotProduct(shiftedX, originalY).getReal(), 1.0e-10);
        Assertions.assertEquals( 0.0,                 FieldVector3D.dotProduct(shiftedX, originalZ).getReal(), 1.0e-10);
        Assertions.assertEquals(-FastMath.sin(rate * dt), FieldVector3D.dotProduct(shiftedY, originalX).getReal(), 1.0e-10);
        Assertions.assertEquals( FastMath.cos(rate * dt), FieldVector3D.dotProduct(shiftedY, originalY).getReal(), 1.0e-10);
        Assertions.assertEquals( 0.0,                 FieldVector3D.dotProduct(shiftedY, originalZ).getReal(), 1.0e-10);
        Assertions.assertEquals( 0.0,                 FieldVector3D.dotProduct(shiftedZ, originalX).getReal(), 1.0e-10);
        Assertions.assertEquals( 0.0,                 FieldVector3D.dotProduct(shiftedZ, originalY).getReal(), 1.0e-10);
        Assertions.assertEquals( 1.0,                 FieldVector3D.dotProduct(shiftedZ, originalZ).getReal(), 1.0e-10);

        FieldVector3D<DerivativeStructure> forward = FieldAngularCoordinates.estimateRate(angularCoordinates.getRotation(), shifted.getRotation(), dt);
        Assertions.assertEquals(0.0, forward.subtract(angularCoordinates.getRotationRate()).getNorm().getReal(), 1.0e-10);

        FieldVector3D<DerivativeStructure> reversed = FieldAngularCoordinates.estimateRate(shifted.getRotation(), angularCoordinates.getRotation(), dt);
        Assertions.assertEquals(0.0, reversed.add(angularCoordinates.getRotationRate()).getNorm().getReal(), 1.0e-10);

    }

    @Test
    public void testReverseOffset() {
        Random random = new Random(0x4ecca9d57a8f1611l);
        for (int i = 0; i < 100; ++i) {
            FieldRotation<DerivativeStructure> r = randomRotation(random);
            FieldVector3D<DerivativeStructure> o = randomVector(random, 1.0e-3);
            FieldVector3D<DerivativeStructure> a = randomVector(random, 1.0e-3);
            TimeStampedFieldAngularCoordinates<DerivativeStructure> ac =
        new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH, r, o, a);
            TimeStampedFieldAngularCoordinates<DerivativeStructure> sum = ac.addOffset(ac.revert());
            Assertions.assertEquals(0.0, sum.getRotation().getAngle().getReal(), 1.0e-15);
            Assertions.assertEquals(0.0, sum.getRotationRate().getNorm().getReal(), 1.0e-15);
            Assertions.assertEquals(0.0, sum.getRotationAcceleration().getNorm().getReal(), 1.0e-15);
        }
    }

    @Test
    public void testNoCommute() {
        TimeStampedFieldAngularCoordinates<DerivativeStructure> ac1 =
                new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH,
                                                         createRotation(0.48,  0.64, 0.36, 0.48, false),
                                                         createVector(0, 0, 0, 4),
                                                         createVector(0, 0, 0, 4));
        TimeStampedFieldAngularCoordinates<DerivativeStructure> ac2 =
                new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH,
                                                         createRotation(0.36, -0.48, 0.48, 0.64, false),
                                                         createVector(0, 0, 0, 4),
                                                         createVector(0, 0, 0, 4));

        TimeStampedFieldAngularCoordinates<DerivativeStructure> add12 = ac1.addOffset(ac2);
        TimeStampedFieldAngularCoordinates<DerivativeStructure> add21 = ac2.addOffset(ac1);

        // the rotations are really different from each other
        Assertions.assertEquals(2.574, FieldRotation.distance(add12.getRotation(), add21.getRotation()).getReal(), 1.0e-3);

    }

    @Test
    public void testRoundTripNoOp() {
        Random random = new Random(0x1e610cfe89306669l);
        for (int i = 0; i < 100; ++i) {

            FieldRotation<DerivativeStructure> r1 = randomRotation(random);
            FieldVector3D<DerivativeStructure> o1 = randomVector(random, 1.0e-2);
            FieldVector3D<DerivativeStructure> a1 = randomVector(random, 1.0e-2);
            TimeStampedFieldAngularCoordinates<DerivativeStructure> ac1 =
        new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH, r1, o1, a1);
            FieldRotation<DerivativeStructure> r2 = randomRotation(random);
            FieldVector3D<DerivativeStructure> o2 = randomVector(random, 1.0e-2);
            FieldVector3D<DerivativeStructure> a2 = randomVector(random, 1.0e-2);

            TimeStampedFieldAngularCoordinates<DerivativeStructure> ac2 =
        new TimeStampedFieldAngularCoordinates<>(AbsoluteDate.J2000_EPOCH, r2, o2, a2);
            TimeStampedFieldAngularCoordinates<DerivativeStructure> roundTripSA = ac1.subtractOffset(ac2).addOffset(ac2);
            Assertions.assertEquals(0.0, FieldRotation.distance(ac1.getRotation(), roundTripSA.getRotation()).getReal(), 4.0e-16);
            Assertions.assertEquals(0.0, FieldVector3D.distance(ac1.getRotationRate(), roundTripSA.getRotationRate()).getReal(), 2.0e-17);
            Assertions.assertEquals(0.0, FieldVector3D.distance(ac1.getRotationAcceleration(), roundTripSA.getRotationAcceleration()).getReal(), 1.0e-17);

            TimeStampedFieldAngularCoordinates<DerivativeStructure> roundTripAS = ac1.addOffset(ac2).subtractOffset(ac2);
            Assertions.assertEquals(0.0, FieldRotation.distance(ac1.getRotation(), roundTripAS.getRotation()).getReal(), 6.0e-16);
            Assertions.assertEquals(0.0, FieldVector3D.distance(ac1.getRotationRate(), roundTripAS.getRotationRate()).getReal(), 2.0e-17);
            Assertions.assertEquals(0.0, FieldVector3D.distance(ac1.getRotationAcceleration(), roundTripAS.getRotationAcceleration()).getReal(), 2.0e-17);

        }
    }

    @Test
    public void testInterpolationNeedOffsetWrongRate() {
        AbsoluteDate date = AbsoluteDate.GALILEO_EPOCH;
        double omega  = 2.0 * FastMath.PI;
        TimeStampedFieldAngularCoordinates<DerivativeStructure> reference =
                new TimeStampedFieldAngularCoordinates<>(date,
                                                         createRotation(1, 0, 0, 0, false),
                                                         createVector(0, 0, -omega, 4),
                                                         createVector(0, 0, 0, 4));

        List<TimeStampedFieldAngularCoordinates<DerivativeStructure>> sample =
                new ArrayList<TimeStampedFieldAngularCoordinates<DerivativeStructure>>();
        for (double dt : new double[] { 0.0, 0.25, 0.5, 0.75, 1.0 }) {
            TimeStampedFieldAngularCoordinates<DerivativeStructure> shifted = reference.shiftedBy(dt);
            sample.add(new TimeStampedFieldAngularCoordinates<>(shifted.getDate(),
                                                                shifted.getRotation(),
                                                                createVector(0, 0, 0, 4),
                                                                createVector(0, 0, 0, 4)));
        }

        for (TimeStampedFieldAngularCoordinates<DerivativeStructure> s : sample) {
            TimeStampedFieldAngularCoordinates<DerivativeStructure> interpolated =
                    TimeStampedFieldAngularCoordinates.interpolate(s.getDate(), AngularDerivativesFilter.USE_RR, sample);
            FieldRotation<DerivativeStructure> r            = interpolated.getRotation();
            FieldVector3D<DerivativeStructure> rate         = interpolated.getRotationRate();
            Assertions.assertEquals(0.0, FieldRotation.distance(s.getRotation(), r).getReal(), 2.0e-14);
            Assertions.assertEquals(0.0, FieldVector3D.distance(s.getRotationRate(), rate).getReal(), 2.0e-13);
        }

    }

    @Test
    public void testInterpolationRotationOnly() {
        AbsoluteDate date = AbsoluteDate.GALILEO_EPOCH;
        double alpha0 = 0.5 * FastMath.PI;
        double omega  = 0.5 * FastMath.PI;
        TimeStampedFieldAngularCoordinates<DerivativeStructure> reference =
                new TimeStampedFieldAngularCoordinates<>(date,
                                                         createRotation(createVector(0, 0, 1, 4), alpha0),
                                                         new FieldVector3D<>(omega, createVector(0, 0, -1, 4)),
                                                         createVector(0, 0, 0, 4));

        List<TimeStampedFieldAngularCoordinates<DerivativeStructure>> sample =
                new ArrayList<TimeStampedFieldAngularCoordinates<DerivativeStructure>>();
        for (double dt : new double[] { 0.0, 0.2, 0.4, 0.6, 0.8, 1.0 }) {
            FieldRotation<DerivativeStructure> r = reference.shiftedBy(dt).getRotation();
            sample.add(new TimeStampedFieldAngularCoordinates<>(date.shiftedBy(dt),
                                                                r,
                                                                createVector(0, 0, 0, 4),
                                                                createVector(0, 0, 0, 4)));
        }

        for (double dt = 0; dt < 1.0; dt += 0.001) {
            TimeStampedFieldAngularCoordinates<DerivativeStructure> interpolated =
                    TimeStampedFieldAngularCoordinates.interpolate(date.shiftedBy(dt), AngularDerivativesFilter.USE_R, sample);
            FieldRotation<DerivativeStructure> r            = interpolated.getRotation();
            FieldVector3D<DerivativeStructure> rate         = interpolated.getRotationRate();
            FieldVector3D<DerivativeStructure> acceleration = interpolated.getRotationAcceleration();
            Assertions.assertEquals(0.0, FieldRotation.distance(reference.shiftedBy(dt).getRotation(), r).getReal(), 3.0e-4);
            Assertions.assertEquals(0.0, FieldVector3D.distance(reference.shiftedBy(dt).getRotationRate(), rate).getReal(), 1.0e-2);
            Assertions.assertEquals(0.0, FieldVector3D.distance(reference.shiftedBy(dt).getRotationAcceleration(), acceleration).getReal(), 1.0e-2);
        }

    }

    @Test
    public void testInterpolationAroundPI() {

        DSFactory factory = new DSFactory(4, 1);
        List<TimeStampedFieldAngularCoordinates<DerivativeStructure>> sample =
                new ArrayList<TimeStampedFieldAngularCoordinates<DerivativeStructure>>();

        // add angular coordinates at t0: 179.999 degrees rotation along X axis
        AbsoluteDate t0 = new AbsoluteDate("2012-01-01T00:00:00.000", TimeScalesFactory.getTAI());
        TimeStampedFieldAngularCoordinates<DerivativeStructure> ac0 =
                new TimeStampedFieldAngularCoordinates<>(t0,
                                                         new FieldRotation<>(createVector(1, 0, 0, 4),
                                                                             factory.variable(3, FastMath.toRadians(179.999)),
                                                                             RotationConvention.VECTOR_OPERATOR),
                                                         createVector(FastMath.toRadians(0), 0, 0, 4),
                                                         createVector(0, 0, 0, 4));
        sample.add(ac0);

        // add angular coordinates at t1: -179.999 degrees rotation (= 180.001 degrees) along X axis
        AbsoluteDate t1 = new AbsoluteDate("2012-01-01T00:00:02.000", TimeScalesFactory.getTAI());
        TimeStampedFieldAngularCoordinates<DerivativeStructure> ac1 =
                new TimeStampedFieldAngularCoordinates<>(t1,
                                                         new FieldRotation<>(createVector(1, 0, 0, 4),
                                                                             factory.variable(3, FastMath.toRadians(-179.999)),
                                                                             RotationConvention.VECTOR_OPERATOR),
                                                         createVector(FastMath.toRadians(0), 0, 0, 4),
                                                         createVector(0, 0, 0, 4));
        sample.add(ac1);

        // get interpolated angular coordinates at mid time between t0 and t1
        AbsoluteDate t = new AbsoluteDate("2012-01-01T00:00:01.000", TimeScalesFactory.getTAI());
        TimeStampedFieldAngularCoordinates<DerivativeStructure> interpolated =
                TimeStampedFieldAngularCoordinates.interpolate(t, AngularDerivativesFilter.USE_R, sample);

        Assertions.assertEquals(FastMath.toRadians(180), interpolated.getRotation().getAngle().getReal(), 1.0e-12);

    }

    @Test
    public void testInterpolationTooSmallSample() {
        DSFactory factory = new DSFactory(4, 1);
        AbsoluteDate date = AbsoluteDate.GALILEO_EPOCH;
        double alpha0 = 0.5 * FastMath.PI;
        double omega  = 0.5 * FastMath.PI;
        TimeStampedFieldAngularCoordinates<DerivativeStructure>  reference =
                new TimeStampedFieldAngularCoordinates<>(date,
                                                         new FieldRotation<>(createVector(0, 0, 1, 4),
                                                                             factory.variable(3, alpha0),
                                                                             RotationConvention.VECTOR_OPERATOR),
                                                         createVector(0, 0, -omega, 4),
                                                         createVector(0, 0, 0, 4));

        List<TimeStampedFieldAngularCoordinates<DerivativeStructure> > sample =
                new ArrayList<TimeStampedFieldAngularCoordinates<DerivativeStructure> >();
        FieldRotation<DerivativeStructure> r = reference.shiftedBy(0.2).getRotation();
        sample.add(new TimeStampedFieldAngularCoordinates<>(date.shiftedBy(0.2),
                                                            r,
                                                            createVector(0, 0, 0, 4),
                                                            createVector(0, 0, 0, 4)));

        try {
            TimeStampedFieldAngularCoordinates.interpolate(date.shiftedBy(0.3), AngularDerivativesFilter.USE_R, sample);
            Assertions.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assertions.assertEquals(OrekitMessages.NOT_ENOUGH_DATA_FOR_INTERPOLATION, oe.getSpecifier());
            Assertions.assertEquals(1, ((Integer) oe.getParts()[0]).intValue());
        }

    }

    @Test
    public void testInterpolationGTODIssue() {
        AbsoluteDate t0 = new AbsoluteDate("2004-04-06T19:59:28.000", TimeScalesFactory.getTAI());
        double[][] params = new double[][] {
            { 0.0, -0.3802356750911964, -0.9248896320037013, 7.292115030462892e-5 },
            { 4.0,  0.1345716955788532, -0.990903859488413,  7.292115033301528e-5 },
            { 8.0, -0.613127541102373,   0.7899839354960061, 7.292115037371062e-5 }
        };
        List<TimeStampedFieldAngularCoordinates<DerivativeStructure>> sample =
                new ArrayList<TimeStampedFieldAngularCoordinates<DerivativeStructure>>();
        for (double[] row : params) {
            AbsoluteDate t = t0.shiftedBy(row[0] * 3600.0);
            FieldRotation<DerivativeStructure>     r = createRotation(row[1], 0.0, 0.0, row[2], false);
            FieldVector3D<DerivativeStructure>     o = new FieldVector3D<>(row[3], createVector(0, 0, 1, 4));
            sample.add(new TimeStampedFieldAngularCoordinates<>(t, r, o, createVector(0, 0, 0, 4)));
        }
        for (double dt = 0; dt < 29000; dt += 120) {
            TimeStampedFieldAngularCoordinates<DerivativeStructure> shifted      = sample.get(0).shiftedBy(dt);
            TimeStampedFieldAngularCoordinates<DerivativeStructure> interpolated =
                    TimeStampedFieldAngularCoordinates.interpolate(t0.shiftedBy(dt), AngularDerivativesFilter.USE_RR, sample);
            Assertions.assertEquals(0.0,
                                FieldRotation.distance(shifted.getRotation(), interpolated.getRotation()).getReal(),
                                1.3e-7);
            Assertions.assertEquals(0.0,
                                FieldVector3D.distance(shifted.getRotationRate(), interpolated.getRotationRate()).getReal(),
                                1.0e-11);
        }

    }

    @Test
    public void testDerivativesStructures0() {
        RandomGenerator random = new Well1024a(0x18a0a08fd63f047al);

        FieldRotation<Decimal64> r    = randomRotation64(random);
        FieldVector3D<Decimal64> o    = randomVector64(random, 1.0e-2);
        FieldVector3D<Decimal64> oDot = randomVector64(random, 1.0e-2);
        TimeStampedFieldAngularCoordinates<Decimal64> ac =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 r, o, oDot);
        TimeStampedFieldAngularCoordinates<Decimal64> rebuilt =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 ac.toDerivativeStructureRotation(0));
        Assertions.assertEquals(0.0, FieldRotation.distance(ac.getRotation(), rebuilt.getRotation()).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, rebuilt.getRotationRate().getNorm().getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, rebuilt.getRotationAcceleration().getNorm().getReal(), 1.0e-15);
    }

    @Test
    public void testDerivativesStructures1() {
        RandomGenerator random = new Well1024a(0x8f8fc6d27bbdc46dl);

        FieldRotation<Decimal64> r    = randomRotation64(random);
        FieldVector3D<Decimal64> o    = randomVector64(random, 1.0e-2);
        FieldVector3D<Decimal64> oDot = randomVector64(random, 1.0e-2);
        TimeStampedFieldAngularCoordinates<Decimal64> ac =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 r, o, oDot);
        TimeStampedFieldAngularCoordinates<Decimal64> rebuilt =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 ac.toDerivativeStructureRotation(1));
        Assertions.assertEquals(0.0, FieldRotation.distance(ac.getRotation(), rebuilt.getRotation()).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, FieldVector3D.distance(ac.getRotationRate(), rebuilt.getRotationRate()).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, rebuilt.getRotationAcceleration().getNorm().getReal(), 1.0e-15);
    }

    @Test
    public void testDerivativesStructures2() {
        RandomGenerator random = new Well1024a(0x1633878dddac047dl);

        FieldRotation<Decimal64> r    = randomRotation64(random);
        FieldVector3D<Decimal64> o    = randomVector64(random, 1.0e-2);
        FieldVector3D<Decimal64> oDot = randomVector64(random, 1.0e-2);
        TimeStampedFieldAngularCoordinates<Decimal64> ac =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 r, o, oDot);
        TimeStampedFieldAngularCoordinates<Decimal64> rebuilt =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 ac.toDerivativeStructureRotation(2));
        Assertions.assertEquals(0.0, FieldRotation.distance(ac.getRotation(), rebuilt.getRotation()).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, FieldVector3D.distance(ac.getRotationRate(), rebuilt.getRotationRate()).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, FieldVector3D.distance(ac.getRotationAcceleration(), rebuilt.getRotationAcceleration()).getReal(), 1.0e-15);

    }

    @Test
    public void testUnivariateDerivative1() {
        RandomGenerator random = new Well1024a(0x6de8cce747539904l);

        FieldRotation<Decimal64> r    = randomRotation64(random);
        FieldVector3D<Decimal64> o    = randomVector64(random, 1.0e-2);
        FieldVector3D<Decimal64> oDot = randomVector64(random, 1.0e-2);
        TimeStampedFieldAngularCoordinates<Decimal64> ac =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 r, o, oDot);
        FieldRotation<FieldUnivariateDerivative1<Decimal64>> rotationUD = ac.toUnivariateDerivative1Rotation();
        FieldRotation<FieldDerivativeStructure<Decimal64>>   rotationDS = ac.toDerivativeStructureRotation(1);
        Assertions.assertEquals(rotationDS.getQ0().getReal(), rotationUD.getQ0().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ1().getReal(), rotationUD.getQ1().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ2().getReal(), rotationUD.getQ2().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ3().getReal(), rotationUD.getQ3().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ0().getPartialDerivative(1).getReal(), rotationUD.getQ0().getFirstDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ1().getPartialDerivative(1).getReal(), rotationUD.getQ1().getFirstDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ2().getPartialDerivative(1).getReal(), rotationUD.getQ2().getFirstDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ3().getPartialDerivative(1).getReal(), rotationUD.getQ3().getFirstDerivative().getReal(), 1.0e-15);

        TimeStampedFieldAngularCoordinates<Decimal64> rebuilt =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 rotationUD);
        Assertions.assertEquals(0.0, FieldRotation.distance(ac.getRotation(), rebuilt.getRotation()).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, FieldVector3D.distance(ac.getRotationRate(), rebuilt.getRotationRate()).getReal(), 1.0e-15);

    }

    @Test
    public void testUnivariateDerivative2() {
        RandomGenerator random = new Well1024a(0x255710c8fa2247ecl);

        FieldRotation<Decimal64> r    = randomRotation64(random);
        FieldVector3D<Decimal64> o    = randomVector64(random, 1.0e-2);
        FieldVector3D<Decimal64> oDot = randomVector64(random, 1.0e-2);
        TimeStampedFieldAngularCoordinates<Decimal64> ac =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 r, o, oDot);
        FieldRotation<FieldUnivariateDerivative2<Decimal64>> rotationUD = ac.toUnivariateDerivative2Rotation();
        FieldRotation<FieldDerivativeStructure<Decimal64>>   rotationDS = ac.toDerivativeStructureRotation(2);
        Assertions.assertEquals(rotationDS.getQ0().getReal(), rotationUD.getQ0().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ1().getReal(), rotationUD.getQ1().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ2().getReal(), rotationUD.getQ2().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ3().getReal(), rotationUD.getQ3().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ0().getPartialDerivative(1).getReal(), rotationUD.getQ0().getFirstDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ1().getPartialDerivative(1).getReal(), rotationUD.getQ1().getFirstDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ2().getPartialDerivative(1).getReal(), rotationUD.getQ2().getFirstDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ3().getPartialDerivative(1).getReal(), rotationUD.getQ3().getFirstDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ0().getPartialDerivative(2).getReal(), rotationUD.getQ0().getSecondDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ1().getPartialDerivative(2).getReal(), rotationUD.getQ1().getSecondDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ2().getPartialDerivative(2).getReal(), rotationUD.getQ2().getSecondDerivative().getReal(), 1.0e-15);
        Assertions.assertEquals(rotationDS.getQ3().getPartialDerivative(2).getReal(), rotationUD.getQ3().getSecondDerivative().getReal(), 1.0e-15);

        TimeStampedFieldAngularCoordinates<Decimal64> rebuilt =
                        new TimeStampedFieldAngularCoordinates<>(FieldAbsoluteDate.getGalileoEpoch(Decimal64Field.getInstance()),
                                                                 rotationUD);
        Assertions.assertEquals(0.0, FieldRotation.distance(ac.getRotation(), rebuilt.getRotation()).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, FieldVector3D.distance(ac.getRotationRate(), rebuilt.getRotationRate()).getReal(), 1.0e-15);
        Assertions.assertEquals(0.0, FieldVector3D.distance(ac.getRotationAcceleration(), rebuilt.getRotationAcceleration()).getReal(), 1.0e-15);

    }

    @Test
    public void testIssue773() {
        doTestIssue773(Decimal64Field.getInstance());
    }

    private <T extends CalculusFieldElement<T>> void doTestIssue773(final Field<T> field) {
        // Epoch
        final AbsoluteDate date = new AbsoluteDate();

        // Coordinates
        final TimeStampedAngularCoordinates angular =
                        new TimeStampedAngularCoordinates(date,
                                                          new Rotation(0., 0., 0., 0., false),
                                                          Vector3D.ZERO,
                                                          Vector3D.ZERO);

        // Time stamped object
        final FieldTimeStamped<T> timeStamped =
                        new TimeStampedFieldAngularCoordinates<>(field, angular);

        // Verify
        Assertions.assertEquals(0.0, date.durationFrom(timeStamped.getDate().toAbsoluteDate()), Double.MIN_VALUE);
    }

    private FieldVector3D<DerivativeStructure> randomVector(Random random, double norm) {
        double n = random.nextDouble() * norm;
        double x = random.nextDouble();
        double y = random.nextDouble();
        double z = random.nextDouble();
        return new FieldVector3D<>(n, createVector(x, y, z, 4).normalize());
    }

    private FieldRotation<DerivativeStructure> randomRotation(Random random) {
        double q0 = random.nextDouble() * 2 - 1;
        double q1 = random.nextDouble() * 2 - 1;
        double q2 = random.nextDouble() * 2 - 1;
        double q3 = random.nextDouble() * 2 - 1;
        double q  = FastMath.sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3);
        return createRotation(q0 / q, q1 / q, q2 / q, q3 / q, false);
    }

    private FieldRotation<DerivativeStructure> createRotation(FieldVector3D<DerivativeStructure> axis, double angle) {
        return new FieldRotation<>(axis,
                                   new DSFactory(4, 1).constant(angle),
                                   RotationConvention.VECTOR_OPERATOR);
    }

    private FieldRotation<DerivativeStructure> createRotation(double q0, double q1, double q2, double q3,
                                      boolean needsNormalization) {
        DSFactory factory = new DSFactory(4, 1);
        return new FieldRotation<>(factory.variable(0, q0),
                                   factory.variable(1, q1),
                                   factory.variable(2, q2),
                                   factory.variable(3, q3),
                                   needsNormalization);
    }

    private FieldVector3D<DerivativeStructure> createVector(double x, double y, double z, int params) {
        DSFactory factory = new DSFactory(params, 1);
        return new FieldVector3D<>(factory.variable(0, x),
                                   factory.variable(1, y),
                                   factory.variable(2, z));
    }

    private FieldRotation<Decimal64> randomRotation64(RandomGenerator random) {
        double q0 = random.nextDouble() * 2 - 1;
        double q1 = random.nextDouble() * 2 - 1;
        double q2 = random.nextDouble() * 2 - 1;
        double q3 = random.nextDouble() * 2 - 1;
        double q  = FastMath.sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3);
        return new FieldRotation<>(new Decimal64(q0 / q),
                                   new Decimal64(q1 / q),
                                   new Decimal64(q2 / q),
                                   new Decimal64(q3 / q),
                                   false);
    }

    private FieldVector3D<Decimal64> randomVector64(RandomGenerator random, double norm) {
        double n = random.nextDouble() * norm;
        double x = random.nextDouble();
        double y = random.nextDouble();
        double z = random.nextDouble();
        return new FieldVector3D<>(n, new FieldVector3D<>(new Decimal64(x), new Decimal64(y), new Decimal64(z)).normalize());
    }

}

