#ifndef FROZENDAWN_MASTER_ARCHITECT_EYE_VOLUME_GLSL
#define FROZENDAWN_MASTER_ARCHITECT_EYE_VOLUME_GLSL

const int FD_EYE_STORM_STEPS = 8;

float fdEyeHash31(vec3 point) {
    point = fract(point * 0.1031);
    point += dot(point, point.yzx + 33.33);
    return fract((point.x + point.y) * point.z);
}

float fdEyeNoise3(vec3 point) {
    vec3 cell = floor(point);
    vec3 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);

    float n000 = fdEyeHash31(cell + vec3(0.0, 0.0, 0.0));
    float n100 = fdEyeHash31(cell + vec3(1.0, 0.0, 0.0));
    float n010 = fdEyeHash31(cell + vec3(0.0, 1.0, 0.0));
    float n110 = fdEyeHash31(cell + vec3(1.0, 1.0, 0.0));
    float n001 = fdEyeHash31(cell + vec3(0.0, 0.0, 1.0));
    float n101 = fdEyeHash31(cell + vec3(1.0, 0.0, 1.0));
    float n011 = fdEyeHash31(cell + vec3(0.0, 1.0, 1.0));
    float n111 = fdEyeHash31(cell + vec3(1.0, 1.0, 1.0));

    float lower = mix(
        mix(n000, n100, local.x),
        mix(n010, n110, local.x),
        local.y);
    float upper = mix(
        mix(n001, n101, local.x),
        mix(n011, n111, local.x),
        local.y);
    return mix(lower, upper, local.z);
}

float fdEyeFbm(vec3 point) {
    float value = 0.0;
    float weight = 0.56;
    for (int octave = 0; octave < 2; octave++) {
        value += fdEyeNoise3(point) * weight;
        point = point * 2.03 + vec3(13.1, 7.7, 19.3);
        weight *= 0.48;
    }
    return value * 1.16;
}

mat2 fdEyeRotate(float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return mat2(cosine, -sine, sine, cosine);
}

vec2 fdEyeIntersectBounds(
        vec3 rayOrigin,
        vec3 rayDirection,
        vec3 eyeCenter,
        float outerRadius,
        float minimumY,
        float maximumY,
        float maximumDistance) {
    vec2 horizontalOrigin = rayOrigin.xz - eyeCenter.xz;
    float a = dot(rayDirection.xz, rayDirection.xz);
    float cylinderEntry = 0.0;
    float cylinderExit = maximumDistance;
    if (a < 0.000001) {
        if (dot(horizontalOrigin, horizontalOrigin)
                > outerRadius * outerRadius) {
            return vec2(1.0, -1.0);
        }
    } else {
        float b = 2.0 * dot(horizontalOrigin, rayDirection.xz);
        float c = dot(horizontalOrigin, horizontalOrigin)
                - outerRadius * outerRadius;
        float discriminant = b * b - 4.0 * a * c;
        if (discriminant <= 0.0) {
            return vec2(1.0, -1.0);
        }
        float root = sqrt(discriminant);
        cylinderEntry = (-b - root) / (2.0 * a);
        cylinderExit = (-b + root) / (2.0 * a);
    }

    float heightEntry = 0.0;
    float heightExit = maximumDistance;
    if (abs(rayDirection.y) < 0.000001) {
        if (rayOrigin.y < minimumY || rayOrigin.y > maximumY) {
            return vec2(1.0, -1.0);
        }
    } else {
        float first = (minimumY - rayOrigin.y) / rayDirection.y;
        float second = (maximumY - rayOrigin.y) / rayDirection.y;
        heightEntry = min(first, second);
        heightExit = max(first, second);
    }

    return vec2(
        max(0.0, max(cylinderEntry, heightEntry)),
        min(maximumDistance, min(cylinderExit, heightExit)));
}

float fdEyeStormDensity(
        vec3 worldPosition,
        vec3 eyeCenter,
        float innerRadius,
        float outerRadius,
        float minimumY,
        float maximumY,
        float time,
        vec2 wind) {
    vec3 local = worldPosition - eyeCenter;
    // Keep the supercell nearly fixed while the weather inside it keeps moving.
    float shapeTime = time * 0.04;
    float weatherTime = time * 0.10;
    float radialDistance = length(local.xz);
    float baseAngle = atan(local.z, local.x);
    float boundaryNoise = 0.5
            + sin(baseAngle * 5.0 + local.y * 0.055
                    - shapeTime * 0.20) * 0.18
            + sin(baseAngle * 11.0 - local.y * 0.027
                    + shapeTime * 0.11) * 0.10;
    float warpedRadialDistance = radialDistance
            - (boundaryNoise - 0.5) * 4.8;
    float middleRadius = (innerRadius + outerRadius) * 0.5;
    float halfWidth = max(0.5, (outerRadius - innerRadius) * 0.5);
    float radial = 1.0 - smoothstep(
        0.42,
        1.0,
        abs(warpedRadialDistance - middleRadius) / halfWidth);

    float normalizedHeight = clamp(
        (worldPosition.y - minimumY) / max(0.1, maximumY - minimumY),
        0.0,
        1.0);
    float bottomFade = smoothstep(0.0, 0.07, normalizedHeight);

    float windStrength = length(wind);
    float swirl = baseAngle
            + shapeTime * (0.34 + windStrength * 0.22)
            + local.y * 0.043;
    vec2 spun = fdEyeRotate(swirl) * vec2(radialDistance, 0.0);
    vec3 samplePoint = vec3(spun.x, local.y, spun.y) * 0.095;
    samplePoint.xz += wind * weatherTime * 0.16;
    samplePoint.y -= weatherTime * 0.050;

    float broadNoise = fdEyeFbm(samplePoint * 0.63);
    float detailNoise = fdEyeNoise3(
        samplePoint * 1.37 + vec3(7.1, 13.7, 3.4));
    float crest = 0.78
            + (broadNoise - 0.5) * 0.24
            + sin(baseAngle * 5.0 + shapeTime * 0.31) * 0.035;
    float topFade = 1.0 - smoothstep(crest - 0.13, crest, normalizedHeight);

    float gust = 0.5 + 0.5 * sin(
        baseAngle * 8.0
        + local.y * 0.19
        - weatherTime * (1.20 + windStrength * 0.30)
        + broadNoise * 5.5);
    float rollingSnow = smoothstep(
        0.34,
        0.79,
        broadNoise * 0.70 + detailNoise * 0.30 + gust * 0.18);
    float fineSnow = pow(max(0.0, detailNoise - 0.48), 2.4) * 1.7;

    return radial * bottomFade * topFade
            * (0.035 + rollingSnow * 1.62 + fineSnow + gust * 0.18);
}

vec2 fdIntegrateEyeStorm(
        vec3 rayOrigin,
        vec3 rayDirection,
        float maximumDistance,
        vec3 eyeCenter,
        float innerRadius,
        float outerRadius,
        float minimumY,
        float maximumY,
        float time,
        vec2 wind,
        float densityScale,
        float jitter) {
    vec2 interval = fdEyeIntersectBounds(
        rayOrigin,
        rayDirection,
        eyeCenter,
        outerRadius + 6.0,
        minimumY,
        maximumY,
        maximumDistance);
    if (interval.y <= interval.x) {
        return vec2(0.0);
    }

    float segmentLength = interval.y - interval.x;
    float stepLength = segmentLength / float(FD_EYE_STORM_STEPS);
    float opticalDepth = 0.0;
    float snowLight = 0.0;
    for (int index = 0; index < FD_EYE_STORM_STEPS; index++) {
        float distanceAlongRay = interval.x
                + (float(index) + jitter) * stepLength;
        vec3 samplePosition = rayOrigin + rayDirection * distanceAlongRay;
        float density = fdEyeStormDensity(
            samplePosition,
            eyeCenter,
            innerRadius,
            outerRadius,
            minimumY,
            maximumY,
            time,
            wind);
        opticalDepth += density * stepLength * densityScale;
        snowLight += smoothstep(0.75, 1.65, density) * stepLength;
    }

    float opacity = 1.0 - exp(-opticalDepth);
    float highlight = clamp(snowLight * 0.055, 0.0, 0.34);
    return vec2(opacity, highlight);
}

#endif
