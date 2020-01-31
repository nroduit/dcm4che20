package org.dcm4che6.img.data;

import org.opencv.core.Mat;
import org.opencv.img_hash.AverageHash;
import org.opencv.img_hash.BlockMeanHash;
import org.opencv.img_hash.ColorMomentHash;
import org.opencv.img_hash.ImgHashBase;
import org.opencv.img_hash.MarrHildrethHash;
import org.opencv.img_hash.PHash;
import org.opencv.img_hash.RadialVarianceHash;

public enum ImageHash {
    AVERAGE() {
        @Override
        public ImgHashBase getAlgorithm() {
            return AverageHash.create();
        }
    },
    PHASH() {
        @Override
        public ImgHashBase getAlgorithm() {
            return PHash.create();
        }
    },
    MARR_HILDRETH() {
        @Override
        public ImgHashBase getAlgorithm() {
            return MarrHildrethHash.create();
        }
    },
    RADIAL_VARIANCE() {
        @Override
        public ImgHashBase getAlgorithm() {
            return RadialVarianceHash.create();
        }
    },
    BLOCK_MEAN_ZERO() {
        @Override
        public ImgHashBase getAlgorithm() {
            return BlockMeanHash.create(0);
        }
    },
    BLOCK_MEAN_ONE() {
        @Override
        public ImgHashBase getAlgorithm() {
            return BlockMeanHash.create(1);
        }
    },
    COLOR_MOMENT() {
        @Override
        public ImgHashBase getAlgorithm() {
            return ColorMomentHash.create();
        }
    };

    public abstract ImgHashBase getAlgorithm();

    public double compare(Mat imgIn, Mat imgOut) {
        ImgHashBase hashAlgo = getAlgorithm();
        Mat inHash = new Mat();
        Mat outHash = new Mat();
        hashAlgo.compute(imgIn, inHash);
        hashAlgo.compute(imgOut, outHash);
        return hashAlgo.compare(inHash, outHash);
    }

}