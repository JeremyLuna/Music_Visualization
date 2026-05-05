export class FFT {
    static neg2pi = -2 * Math.PI;
    static fft_in_place(re, im) {
        const n = re.length;
        const levels = Math.log2(n);
        if (Math.floor(levels) !== levels) {
            throw new Error('FFT size must be power of 2');
        }

        // Bit-reversed addressing
        for (let i = 0; i < n; i++) {
            let j = 0;
            for (let k = 0; k < levels; k++) {
                j = (j << 1) | ((i >>> k) & 1);
            }
            if (j > i) {
                [re[i], re[j]] = [re[j], re[i]];
                [im[i], im[j]] = [im[j], im[i]];
            }
        }

        // Cooley-Tukey FFT
        for (let size = 2; size <= n; size <<= 1) {
            const halfSize = size >> 1;
            const tableStep = n / size;
            const nfactor = this.neg2pi / n;
            for (let i = 0; i < n; i += size) {
                for (let j = 0; j < halfSize; j++) {
                    const k = j * tableStep;
                    const ijh = i + j + halfSize;
                    const trigfactor = nfactor * k;
                    const cosfactor = Math.cos(trigfactor);
                    const sinfactor = Math.sin(trigfactor);
                    const tRe = cosfactor * re[ijh]
                        - sinfactor * im[ijh];
                    const tIm = sinfactor * re[ijh]
                        + cosfactor * im[ijh];
                    re[ijh] = re[i + j] - tRe;
                    im[ijh] = im[i + j] - tIm;
                    re[i + j] += tRe;
                    im[i + j] += tIm;
                }
            }
        }
    }
}