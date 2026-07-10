Engine_Decimator : CroneEngine {
	//Decimate
	var srate=48000, sdepth=32;

	//Saturate
	var crossover=1400,
	distAmount=15, //1-500
	lowbias=0.04, //0.01 - 1
	highbias=0.12, //0.01 - 1
	hissAmount=0.5, //0.0 - 1.0
	cutoff=11500;

	var <saturator;

	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

	alloc {

		~tf =  Env([-0.7, 0, 0.7], [1,1], [8,-8]).asSignal(1025);
		~tf = ~tf + (
			Signal.sineFill(
				1025,
				(0!3) ++ [0,0,1,1,0,1].scramble,
				{rrand(0,2pi)}!9
			)/10;
		);
		~tf = ~tf.normalize;
		~tfBuf = Buffer.loadCollection(context.server, ~tf.asWavetableNoWrap);

		SynthDef(\Saturator, { |inL, inR, out, srate=48000, sdepth=32, crossover=1400, distAmount=15, lowbias=0.04, highbias=0.12, hissAmount=0.5, cutoff=11500, choir_amp = 0.01, choir_tilt = 0|
			var input = Decimator.ar(SoundIn.ar([0,1]),srate, sdepth);
			var crossAmount = 50;

			var mono = (input[0] + input[1]) * 0.5;

			var bass = LPF.ar(mono,330, 1);
			var bass_pitch = Lag.kr(Pitch.kr(bass, 206, 40, 3300)[0]);
			var bass_amp = 0.307 - (0.214 * choir_tilt) - (0.107 * choir_tilt * choir_tilt);
			bass_amp = bass_amp.max(0) * Amplitude.kr(bass, 0.16, .32);

			var tenor = BPF.ar(mono, 330, 1.2, 1);
			var tenor_pitch = Lag.kr(Pitch.kr(tenor,327, 65, 1046)[0]);
			var tenor_amp = 0.388 - (0.107 * choir_tilt) - (0.107 * choir_tilt * choir_tilt);
			tenor_amp = tenor_amp.max(0) * Amplitude.kr(tenor, 0.08, 0.16);

			var alto = BPF.ar(mono, 437, 1.2, 1);
			var alto_pitch = Lag.kr(Pitch.kr(alto, 436, 87, 1396)[0]);
			var alto_amp = 0.388 + (0.107 * choir_tilt) - (0.107 * choir_tilt * choir_tilt);
			alto_amp = alto_amp.max(0) * Amplitude.kr(alto, 0.04, 0.08);

			var soprano = HPF.ar(mono, 261, 1);
			var soprano_pitch = Lag.kr(Pitch.kr(soprano, 654, 130, 2094)[0]);
			var soprano_amp = 0.307 + (0.214 * choir_tilt) - (0.107 * choir_tilt * choir_tilt);
			soprano_amp = soprano_amp.max(0) * Amplitude.kr(soprano, 0.02, 0.04);

			var choir = SinOsc.ar(bass_pitch, 0, bass_amp);
			choir = choir + SinOsc.ar(tenor_pitch, 0, tenor_amp);
			choir = choir + SinOsc.ar(alto_pitch, 0, alto_amp);
			choir = choir + SinOsc.ar(soprano_pitch, 0, soprano_amp);

			var lpf = LPF.ar(
				input,
				crossover + crossAmount,
				1
			) * lowbias;

			var hpf = HPF.ar(
				input,
				crossover - crossAmount,
				1
			) * highbias;

			var beforeHiss = Mix.new([
				Mix.new([lpf,hpf]),
				HPF.ar(Mix.new([PinkNoise.ar(0.001), Dust.ar(5,0.002)]), 2000, hissAmount)
			]);

			var compressed = Compander.ar(beforeHiss, input,
				thresh: 0.2,
				slopeBelow: 1,
				slopeAbove: 0.3,
				clampTime:  0.001,
				relaxTime:  0.1,
			);
			var shaped = Shaper.ar(~tfBuf, compressed  * distAmount);

			var afterHiss = HPF.ar(Mix.new([PinkNoise.ar(1), Dust.ar(5,1)]), 2000, 1);

			var duckedHiss = Compander.ar(afterHiss, input,
				thresh: 0.4,
				slopeBelow: 1,
				slopeAbove: 0.2,
				clampTime: 0.01,
				relaxTime: 0.1,
			) * 0.5 * hissAmount;

			var morehiss = Mix.new([
				duckedHiss,
				Mix.new([lpf * (1 / lowbias) * (distAmount/10), shaped])
			]);

			var decimator_out = Mix.new([input * 0.5, morehiss]);
			var limited = Limiter.ar((decimator_out * (1 - choir_amp)) + (choir * choir_amp),0.9, 0.01);

			Out.ar(out, MoogFF.ar(
				limited,
				cutoff,
				1
			));
		}).add;

		context.server.sync;

		saturator = Synth.new(\Saturator, [
			\inL, context.in_b[0].index,
			\inR, context.in_b[1].index,
			\out, context.out_b.index,
			\srate, 48000,
			\sdepth, 32,
			\crossover, 1400, //500-9k
			\distAmount, 15, //1-500
			\lowbias, 0.04, //0.01 - 1
			\highbias, 0.12, //0.01 - 1
			\hissAmount, 0.2, //0.0 - 1.0
			\choir_amp, 0.01, // 0.0 - 1.0
			\choir_tilt, 0, // -1.0 - 1.0
			\cutoff, 11500],
		context.xg);

		this.addCommand("srate", "i", {|msg|
			saturator.set(\srate, msg[1]);
		});

		this.addCommand("choir_amp", "f", {|msg|
			saturator.set(\choir_amp, msg[1]);
		});

		this.addCommand("choir_tilt", "f", {|msg|
			saturator.set(\choir_tilt, msg[1]);
		});

		this.addCommand("sdepth", "f", {|msg|
			saturator.set(\sdepth, msg[1]);
		});

		this.addCommand("crossover", "i", {|msg|
			saturator.set(\crossover, msg[1]);
		});

		this.addCommand("distAmount", "i", {|msg|
			saturator.set(\distAmount, msg[1]);
		});

		this.addCommand("lowbias", "f", {|msg|
			saturator.set(\lowbias, msg[1]);
		});

		this.addCommand("highbias", "f", {|msg|
			saturator.set(\highbias, msg[1]);
		});

		this.addCommand("hissAmount", "f", {|msg|
			var amp = msg[1]*0.1;
			if(amp>0.001, {amp = amp.linexp(0.001, 1, 0.001, 0.25)});
			saturator.set(\hissAmount, amp);
		});
	}

	free {
		saturator.free;
	}
}
