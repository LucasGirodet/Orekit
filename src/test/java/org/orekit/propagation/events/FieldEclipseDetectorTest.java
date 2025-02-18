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
package org.orekit.propagation.events;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.Field;
import org.hipparchus.exception.MathRuntimeException;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.AdaptiveStepsizeFieldIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince853FieldIntegrator;
import org.hipparchus.util.Decimal64Field;
import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.Utils;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.FieldCartesianOrbit;
import org.orekit.orbits.FieldEquinoctialOrbit;
import org.orekit.orbits.FieldOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.FieldSpacecraftState;
import org.orekit.propagation.analytical.FieldKeplerianPropagator;
import org.orekit.propagation.events.handlers.FieldStopOnDecreasing;
import org.orekit.propagation.numerical.FieldNumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.FieldPVCoordinates;
import org.orekit.utils.TimeStampedFieldPVCoordinates;

public class FieldEclipseDetectorTest {

    private double               mu;
    private CelestialBody        sun;
    private CelestialBody        earth;
    private double               sunRadius;
    private double               earthRadius;


    @BeforeEach
    public void setUp() {
        try {
            Utils.setDataRoot("regular-data");
            sun = CelestialBodyFactory.getSun();
            earth = CelestialBodyFactory.getEarth();
            sunRadius = 696000000.;
            earthRadius = 6400000.;
            mu  = 3.9860047e14;
        } catch (OrekitException oe) {
            Assertions.fail(oe.getLocalizedMessage());
        }
    }

    @Test
    public void testEclipse() {
        doTestEclipse(Decimal64Field.getInstance());
    }
    @Test
    public void testPenumbra() {
        doTestPenumbra(Decimal64Field.getInstance());
    }
    @Test
    public void testWithMethods() {
        doTestWithMethods(Decimal64Field.getInstance());
    }

    @Test
    public void testInsideOcculting() {
        doTestInsideOcculting(Decimal64Field.getInstance());
    }
    @Test
    public void testInsideOcculted() {
        doTestInsideOcculted(Decimal64Field.getInstance());
    }
    @Test
    public void testTooSmallMaxIterationCount() {
        testTooSmallMaxIterationCount(Decimal64Field.getInstance());
    }



    private <T extends CalculusFieldElement<T>> void doTestEclipse(Field<T> field) {
        T zero = field.getZero();
        final FieldVector3D<T> position  = new FieldVector3D<>(zero.add(-6142438.668), zero.add(3492467.560), zero.add(-25767.25680));
        final FieldVector3D<T> velocity  = new FieldVector3D<>(zero.add(505.8479685), zero.add(942.7809215), zero.add(7435.922231));
        FieldAbsoluteDate<T> iniDate = new FieldAbsoluteDate<>(field, 1969, 7, 28, 4, 0, 0.0, TimeScalesFactory.getTT());
        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(new FieldPVCoordinates<>(position,  velocity),
                                                                FramesFactory.getGCRF(), iniDate, zero.add(mu));
        FieldSpacecraftState<T> initialState = new FieldSpacecraftState<>(orbit);
        FieldKeplerianPropagator<T> propagator = new FieldKeplerianPropagator<>(orbit);
        propagator.resetInitialState(initialState);

        FieldEclipseDetector<T> e = new FieldEclipseDetector<>(field.getZero().add(60.), field.getZero().add(1e-3),
                                                               sun, sunRadius,
                                                               earth, earthRadius).
                            withHandler(new FieldStopOnDecreasing<FieldEclipseDetector<T>, T>()).
                            withUmbra();
        Assertions.assertEquals(60.0, e.getMaxCheckInterval().getReal(), 1.0e-15);
        Assertions.assertEquals(1.0e-3, e.getThreshold().getReal(), 1.0e-15);
        Assertions.assertEquals(AbstractDetector.DEFAULT_MAX_ITER, e.getMaxIterationCount());
        Assertions.assertSame(sun, e.getOcculted());
        Assertions.assertEquals(sunRadius, e.getOccultedRadius(), 1.0);
        Assertions.assertSame(earth, e.getOcculting());
        Assertions.assertEquals(earthRadius, e.getOccultingRadius(), 1.0);
        Assertions.assertTrue(e.getTotalEclipse());
        propagator.addEventDetector(e);
        final FieldSpacecraftState<T> finalState = propagator.propagate(iniDate.shiftedBy(6000));
        Assertions.assertEquals(2303.1835, finalState.getDate().durationFrom(iniDate).getReal(), 1.0e-3);
    }

    private <T extends CalculusFieldElement<T>> void doTestPenumbra(Field<T> field) {
        T zero = field.getZero();
        final FieldVector3D<T> position  = new FieldVector3D<>(zero.add(-6142438.668), zero.add(3492467.560), zero.add(-25767.25680));
        final FieldVector3D<T> velocity  = new FieldVector3D<>(zero.add(505.8479685), zero.add(942.7809215), zero.add(7435.922231));
        FieldAbsoluteDate<T> iniDate = new FieldAbsoluteDate<>(field, 1969, 7, 28, 4, 0, 0.0, TimeScalesFactory.getTT());
        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(new FieldPVCoordinates<>(position,  velocity),
                                                                FramesFactory.getGCRF(), iniDate, zero.add(mu));
        FieldSpacecraftState<T> initialState = new FieldSpacecraftState<>(orbit);
        double[] absTolerance = {
            0.001, 1.0e-9, 1.0e-9, 1.0e-6, 1.0e-6, 1.0e-6, 0.001
        };
        double[] relTolerance = {
            1.0e-7, 1.0e-4, 1.0e-4, 1.0e-7, 1.0e-7, 1.0e-7, 1.0e-7
        };
        AdaptiveStepsizeFieldIntegrator<T> integrator =
            new DormandPrince853FieldIntegrator<>(field, 0.001, 1000, absTolerance, relTolerance);
        integrator.setInitialStepSize(60.);
        FieldNumericalPropagator<T> propagator = new FieldNumericalPropagator<>(field, integrator);
        propagator.setOrbitType(OrbitType.EQUINOCTIAL);
        propagator.setInitialState(initialState);
        sun = CelestialBodyFactory.getSun();
        earth = CelestialBodyFactory.getEarth();
        sunRadius = 696000000.;
        earthRadius = 6400000.;

        FieldEclipseDetector<T> e = new FieldEclipseDetector<>(zero.add(60.), zero.add(1.e-3), sun, sunRadius,
                                                               earth, earthRadius).
                            withPenumbra();
        Assertions.assertFalse(e.getTotalEclipse());
        propagator.addEventDetector(e);
        final FieldSpacecraftState<T> finalState = propagator.propagate(iniDate.shiftedBy(6000));
        Assertions.assertEquals(4388.155852, finalState.getDate().durationFrom(iniDate).getReal(), 2.0e-6);
    }

    private <T extends CalculusFieldElement<T>> void doTestWithMethods(Field<T> field) {
        T zero = field.getZero();
        final FieldVector3D<T> position  = new FieldVector3D<>(zero.add(-6142438.668), zero.add(3492467.560), zero.add(-25767.25680));
        final FieldVector3D<T> velocity  = new FieldVector3D<>(zero.add(505.8479685), zero.add(942.7809215), zero.add(7435.922231));
        FieldAbsoluteDate<T> iniDate = new FieldAbsoluteDate<>(field, 1969, 7, 28, 4, 0, 0.0, TimeScalesFactory.getTT());
        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(new FieldPVCoordinates<>(position,  velocity),
                                                                FramesFactory.getGCRF(), iniDate, zero.add(mu));
        FieldSpacecraftState<T> initialState = new FieldSpacecraftState<>(orbit);
        double[] absTolerance = {
            0.001, 1.0e-9, 1.0e-9, 1.0e-6, 1.0e-6, 1.0e-6, 0.001
        };
        double[] relTolerance = {
            1.0e-7, 1.0e-4, 1.0e-4, 1.0e-7, 1.0e-7, 1.0e-7, 1.0e-7
        };
        AdaptiveStepsizeFieldIntegrator<T> integrator =
            new DormandPrince853FieldIntegrator<>(field, 0.001, 1000, absTolerance, relTolerance);
        integrator.setInitialStepSize(60);
        FieldNumericalPropagator<T> propagator = new FieldNumericalPropagator<>(field, integrator);
        propagator.setOrbitType(OrbitType.EQUINOCTIAL);
        propagator.setInitialState(initialState);
        sun = CelestialBodyFactory.getSun();
        earth = CelestialBodyFactory.getEarth();
        sunRadius = 696000000.;
        earthRadius = 6400000.;

        FieldEclipseDetector<T> e = new FieldEclipseDetector<>(field.getZero().add(60.), field.getZero().add(1e-3),
                                                               sun, sunRadius,
                                                               earth, earthRadius).
                             withHandler(new FieldStopOnDecreasing<FieldEclipseDetector<T>, T>()).
                             withMaxCheck(field.getZero().add(120.0)).
                             withThreshold(field.getZero().add(1.0e-4)).
                             withMaxIter(12);
        Assertions.assertEquals(120.0, e.getMaxCheckInterval().getReal(), 1.0e-15);
        Assertions.assertEquals(1.0e-4, e.getThreshold().getReal(), 1.0e-15);
        Assertions.assertEquals(12, e.getMaxIterationCount());
        propagator.addEventDetector(e);
        final FieldSpacecraftState<T> finalState = propagator.propagate(iniDate.shiftedBy(6000));
        Assertions.assertEquals(2303.1835, finalState.getDate().durationFrom(iniDate).getReal(), 1.0e-3);

    }

    private <T extends CalculusFieldElement<T>> void doTestInsideOcculting(Field<T> field) {
        T zero = field.getZero();
        final FieldVector3D<T> position  = new FieldVector3D<>(zero.add(-6142438.668), zero.add(3492467.560), zero.add(-25767.25680));
        final FieldVector3D<T> velocity  = new FieldVector3D<>(zero.add(505.8479685), zero.add(942.7809215), zero.add(7435.922231));
        FieldAbsoluteDate<T> iniDate = new FieldAbsoluteDate<>(field, 1969, 7, 28, 4, 0, 0.0, TimeScalesFactory.getTT());
        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(new FieldPVCoordinates<>(position,  velocity),
                                                 FramesFactory.getGCRF(), iniDate, zero.add(mu));
        FieldSpacecraftState<T> initialState = new FieldSpacecraftState<>(orbit);
        double[] absTolerance = {
            0.001, 1.0e-9, 1.0e-9, 1.0e-6, 1.0e-6, 1.0e-6, 0.001
        };
        double[] relTolerance = {
            1.0e-7, 1.0e-4, 1.0e-4, 1.0e-7, 1.0e-7, 1.0e-7, 1.0e-7
        };
        AdaptiveStepsizeFieldIntegrator<T> integrator =
            new DormandPrince853FieldIntegrator<>(field, 0.001, 1000, absTolerance, relTolerance);
        integrator.setInitialStepSize(60);
        FieldNumericalPropagator<T> propagator = new FieldNumericalPropagator<>(field, integrator);
        propagator.setOrbitType(OrbitType.EQUINOCTIAL);
        propagator.setInitialState(initialState);
        sun = CelestialBodyFactory.getSun();
        earth = CelestialBodyFactory.getEarth();
        sunRadius = 696000000.;
        earthRadius = 6400000.;

        FieldEclipseDetector<T> e = new FieldEclipseDetector<>(field.getZero().add(60.), field.getZero().add(1.e-3),
                                                               sun, sunRadius,
                                                               earth, earthRadius);
        FieldSpacecraftState<T> s = new FieldSpacecraftState<>(new FieldCartesianOrbit<>(new TimeStampedFieldPVCoordinates<>(FieldAbsoluteDate.getJ2000Epoch(field),
                                                                                                                             new FieldPVCoordinates<>(new FieldVector3D<>(field.getZero().add(1e6),
                                                                                                                                                                          field.getZero().add(2e6),
                                                                                                                                                                          field.getZero().add(3e6)),
                                                                                                                                                      new FieldVector3D<>(field.getZero().add(1000),
                                                                                                                                                                          field.getZero().add(0),
                                                                                                                                                                          field.getZero().add(0)))),
                                                                                         FramesFactory.getGCRF(),
                                                                                         zero.add(mu)));
        Assertions.assertEquals(-FastMath.PI, e.g(s).getReal(), 1.0e-15);
    }

    private <T extends CalculusFieldElement<T>> void doTestInsideOcculted(Field<T> field) {
        T zero = field.getZero();
        final FieldVector3D<T> position  = new FieldVector3D<>(zero.add(-6142438.668), zero.add(3492467.560), zero.add(-25767.25680));
        final FieldVector3D<T> velocity  = new FieldVector3D<>(zero.add(505.8479685), zero.add(942.7809215), zero.add(7435.922231));
        FieldAbsoluteDate<T> iniDate = new FieldAbsoluteDate<>(field, 1969, 7, 28, 4, 0, 0.0, TimeScalesFactory.getTT());
        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(new FieldPVCoordinates<>(position,  velocity),
                                                                FramesFactory.getGCRF(), iniDate, zero.add(mu));
        FieldSpacecraftState<T> initialState = new FieldSpacecraftState<>(orbit);
        double[] absTolerance = {
            0.001, 1.0e-9, 1.0e-9, 1.0e-6, 1.0e-6, 1.0e-6, 0.001
        };
        double[] relTolerance = {
            1.0e-7, 1.0e-4, 1.0e-4, 1.0e-7, 1.0e-7, 1.0e-7, 1.0e-7
        };
        AdaptiveStepsizeFieldIntegrator<T> integrator =
            new DormandPrince853FieldIntegrator<>(field, 0.001, 1000, absTolerance, relTolerance);
        integrator.setInitialStepSize(60);
        FieldNumericalPropagator<T> propagator = new FieldNumericalPropagator<>(field, integrator);
        propagator.setOrbitType(OrbitType.EQUINOCTIAL);
        propagator.setInitialState(initialState);
        sun = CelestialBodyFactory.getSun();
        earth = CelestialBodyFactory.getEarth();
        sunRadius = 696000000.;
        earthRadius = 6400000.;

        FieldEclipseDetector<T> e = new FieldEclipseDetector<>(field.getZero().add(60.), field.getZero().add(1.e-3),
                        sun, sunRadius,
                        earth, earthRadius);
        Vector3D p = sun.getPVCoordinates(AbsoluteDate.J2000_EPOCH,
                                          FramesFactory.getGCRF()).getPosition();
        FieldSpacecraftState<T> s = new FieldSpacecraftState<>(new FieldCartesianOrbit<>(new TimeStampedFieldPVCoordinates<>(FieldAbsoluteDate.getJ2000Epoch(field),
                                                                                                                             new FieldPVCoordinates<>(new FieldVector3D<>(field.getOne(),
                                                                                                                                                                          field.getZero(),
                                                                                                                                                                          field.getZero()).add(p),
                                                                                                                                                      new FieldVector3D<>(field.getZero(),
                                                                                                                                                                          field.getZero(),
                                                                                                                                                                          field.getOne()))),
                                                                                         FramesFactory.getGCRF(),
                                                                                         zero.add(mu)));
        Assertions.assertEquals(FastMath.PI, e.g(s).getReal(), 1.0e-15);
    }

    private <T extends CalculusFieldElement<T>> void testTooSmallMaxIterationCount(Field<T> field) {
        T zero = field.getZero();
        final FieldVector3D<T> position  = new FieldVector3D<>(zero.add(-6142438.668), zero.add(3492467.560), zero.add(-25767.25680));
        final FieldVector3D<T> velocity  = new FieldVector3D<>(zero.add(505.8479685), zero.add(942.7809215), zero.add(7435.922231));
        FieldAbsoluteDate<T> iniDate = new FieldAbsoluteDate<>(field, 1969, 7, 28, 4, 0, 0.0, TimeScalesFactory.getTT());
        final FieldOrbit<T> orbit = new FieldEquinoctialOrbit<>(new FieldPVCoordinates<>(position,  velocity),
                                                                FramesFactory.getGCRF(), iniDate, zero.add(mu));
        FieldSpacecraftState<T> initialState = new FieldSpacecraftState<>(orbit);
        double[] absTolerance = {
            0.001, 1.0e-9, 1.0e-9, 1.0e-6, 1.0e-6, 1.0e-6, 0.001
        };
        double[] relTolerance = {
            1.0e-7, 1.0e-4, 1.0e-4, 1.0e-7, 1.0e-7, 1.0e-7, 1.0e-7
        };
        AdaptiveStepsizeFieldIntegrator<T> integrator =
            new DormandPrince853FieldIntegrator<>(field, 0.001, 1000, absTolerance, relTolerance);
        integrator.setInitialStepSize(60);
        FieldNumericalPropagator<T> propagator = new FieldNumericalPropagator<>(field, integrator);
        propagator.setOrbitType(OrbitType.EQUINOCTIAL);
        propagator.setInitialState(initialState);
        sun = CelestialBodyFactory.getSun();
        earth = CelestialBodyFactory.getEarth();
        sunRadius = 696000000.;
        earthRadius = 6400000.;

        int n = 5;
        FieldEclipseDetector<T> e = new FieldEclipseDetector<>(field.getZero().add(60.), field.getZero().add(1.e-3),
                                                               sun, sunRadius,
                                                               earth, earthRadius).
                             withHandler(new FieldStopOnDecreasing<FieldEclipseDetector<T>, T>()).
                             withMaxCheck(field.getZero().add(120.0)).
                             withThreshold(field.getZero().add(1.0e-4)).
                             withMaxIter(n);
       propagator.addEventDetector(e);
        try {
            propagator.propagate(iniDate.shiftedBy(6000));
            Assertions.fail("an exception should have been thrown");
        } catch (OrekitException oe) {
            Assertions.assertEquals(n, ((Integer) ((MathRuntimeException) oe.getCause()).getParts()[0]).intValue());
        }
    }

}
//
