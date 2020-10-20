/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.math4.distribution;

import org.apache.commons.math4.exception.NotStrictlyPositiveException;
import org.apache.commons.math4.exception.util.LocalizedFormats;
import org.apache.commons.rng.UniformRandomProvider;

/**
 * Base class for multivariate probability distributions.
 *
 * @since 3.1
 */
public abstract class AbstractMultivariateRealDistribution
    implements MultivariateRealDistribution {
    /** The number of dimensions or columns in the multivariate distribution. */
    private final int dimension;

    /**
     * @param n Number of dimensions.
     */
    protected AbstractMultivariateRealDistribution(int n) {
        dimension = n;
    }

    /** {@inheritDoc} */
    @Override
    public int getDimension() {
        return dimension;
    }

    /** {@inheritDoc} */
    @Override
    public abstract Sampler createSampler(UniformRandomProvider rng);

    /**
     * Utility function for creating {@code n} vectors generated by the
     * given {@code sampler}.
     *
     * @param n Number of samples.
     * @param sampler Sampler.
     * @return an array of size {@code n} whose elements are random vectors
     * sampled from this distribution.
     */
    public static double[][] sample(int n,
                                    MultivariateRealDistribution.Sampler sampler) {
        if (n <= 0) {
            throw new NotStrictlyPositiveException(LocalizedFormats.NUMBER_OF_SAMPLES,
                                                   n);
        }

        final double[][] samples = new double[n][];
        for (int i = 0; i < n; i++) {
            samples[i] = sampler.sample();
        }
        return samples;
    }
}
