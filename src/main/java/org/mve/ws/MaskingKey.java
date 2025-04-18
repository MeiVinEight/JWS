package org.mve.ws;

import java.util.Random;

public class MaskingKey
{
	private final byte[] masking = new byte[4];
	private int position = 0;
	public boolean having = false;

	public void next(Random random)
	{
		this.reset();
		random.nextBytes(this.masking);
	}

	public void set(byte[] masking)
	{
		System.arraycopy(masking, 0, this.masking, 0, 4);
	}

	public void masking(byte[] data, int pos, int length)
	{
		if (!this.having)
			return;
		for (int i = 0; i < length; i++)
		{
			int si = pos + i;
			data[si] ^= this.masking[this.position];
			this.position++;
			this.position &= 3;
		}
	}

	public void masking(byte[] data)
	{
		this.masking(data, 0, data.length);
	}

	public void reset()
	{
		for (int i = 0; i < this.masking.length; i++)
			masking[i] = 0;
		this.position = 0;
		this.having = false;
	}

	public byte[] value()
	{
		return this.masking.clone();
	}
}
