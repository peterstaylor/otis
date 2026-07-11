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
			var bass, bass_pitch, bass_amp;
			var tenor, tenor_pitch, tenor_amp;
			var alto, alto_pitch, alto_amp;
			var soprano, soprano_pitch, soprano_amp;
			var choir;
			var lpf, hpf;
			var beforeHiss, compressed;
			var shaped, afterHiss, duckedHiss;
			var morehiss, decimator_out, limited;

			bass = LPF.ar(mono,330, 1);
			bass_pitch = Lag.kr(Pitch.kr(bass, 206, 40, 3300)[0], 0.2);
			bass_amp = 0.307 - (0.214 * choir_tilt) - (0.107 * choir_tilt * choir_tilt);
			bass_amp = 4 * bass_amp.max(0) * Amplitude.kr(bass, 0.16, 0.32);

			tenor = BPF.ar(mono, 330, 1.2, 1);
			tenor_pitch = Lag.kr(Pitch.kr(tenor,327, 65, 1046)[0], 0.2);
			tenor_amp = 0.388 - (0.107 * choir_tilt) - (0.107 * choir_tilt * choir_tilt);
			tenor_amp = 4 * tenor_amp.max(0) * Amplitude.kr(tenor, 0.08, 0.16);

			alto = BPF.ar(mono, 437, 1.2, 1);
			alto_pitch = Lag.kr(Pitch.kr(alto, 436, 87, 1396)[0], 0.2);
			alto_amp = 0.388 + (0.107 * choir_tilt) - (0.107 * choir_tilt * choir_tilt);
			alto_amp = 4 * alto_amp.max(0) * Amplitude.kr(alto, 0.04, 0.08);

			soprano = HPF.ar(mono, 261, 1);
			soprano_pitch = Lag.kr(Pitch.kr(soprano, 654, 130, 2094)[0], 0.2);
			soprano_amp = 0.307 + (0.214 * choir_tilt) - (0.107 * choir_tilt * choir_tilt);
			soprano_amp = 4 * soprano_amp.max(0) * Amplitude.kr(soprano, 0.02, 0.04);

			choir = SinOsc.ar(bass_pitch, 0, bass_amp);
			choir = choir + SinOsc.ar(tenor_pitch, 0, tenor_amp);
			choir = choir + SinOsc.ar(alto_pitch, 0, alto_amp);
			choir = choir + SinOsc.ar(soprano_pitch, 0, soprano_amp);

			lpf = LPF.ar(
				choir,
				crossover + crossAmount,
				1
			) * lowbias;

			hpf = HPF.ar(
				choir,
				crossover - crossAmount,
				1
			) * highbias;

			beforeHiss = Mix.new([
				Mix.new([lpf,hpf]),
				HPF.ar(Mix.new([PinkNoise.ar(0.001), Dust.ar(5,0.002)]), 2000, hissAmount)
			]);

			compressed = Compander.ar(beforeHiss, choir,
				thresh: 0.2,
				slopeBelow: 1,
				slopeAbove: 0.3,
				clampTime:  0.001,
				relaxTime:  0.1,
			);
			shaped = Shaper.ar(~tfBuf, compressed  * distAmount);

			afterHiss = HPF.ar(Mix.new([PinkNoise.ar(1), Dust.ar(5,1)]), 2000, 1);

			duckedHiss = Compander.ar(afterHiss, choir,
				thresh: 0.4,
				slopeBelow: 1,
				slopeAbove: 0.2,
				clampTime: 0.01,
				relaxTime: 0.1,
			) * 0.5 * hissAmount;

			morehiss = Mix.new([
				duckedHiss,
				Mix.new([lpf * (1 / lowbias) * (distAmount/10), shaped])
			]);

			decimator_out = Mix.new([choir * 4 * choir_amp, morehiss * (1-decimator_out)]);
			limited = Limiter.ar(decimator_out,0.9, 0.01);

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
