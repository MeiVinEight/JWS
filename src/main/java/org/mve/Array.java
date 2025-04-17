package org.mve;

public class Array
{
	private byte[] array;
	private int head = 0;
	private int tail = 0;

	public Array(int capacity)
	{
		if (capacity <= 0) capacity = 1;
		this.array = new byte[capacity + 1];
	}

	public void put(int b)
	{
		this.expand(this.capacity() + 1);
		this.array[this.head++] = (byte) b;
		this.head %= this.array.length;
	}

	public void put(byte[] buf)
	{
		this.put(buf, 0, buf.length);
	}

	public void put(byte[] buf, int off, int len)
	{
		if (len <= 0) return;
		if (off + len > buf.length) throw new ArrayIndexOutOfBoundsException(off + len);
		this.expand(this.length() + len);
		int clen = len;
		if (clen > this.array.length - this.head) clen = this.array.length - this.head;
		System.arraycopy(buf, off, this.array, this.head, clen);
		off += clen;
		len -= clen;
		this.head += clen;
		this.head %= this.array.length;
		if (len > 0) System.arraycopy(buf, off, this.array, this.head, len);
		this.head += len;
	}

	public void put(int idx, int b)
	{
		if (this.length() == 0) throw new ArrayIndexOutOfBoundsException(idx);
		this.array[(this.tail + idx) % this.array.length] = (byte) b;
	}

	public byte front()
	{
		if (this.length() == 0) return -1;
		return this.array[this.tail];
	}

	public int get()
	{
		if (this.length() == 0) return -1;
		byte b = this.array[this.tail++];
		this.tail %= this.array.length;
		return b & 0xFF;
	}

	public void get(byte[] buf)
	{
		this.get(buf, 0, buf.length);
	}

	public void get(byte[] buf, int off, int len)
	{
		if (len <= 0) return;
		if (len > this.length() || (off + len) > buf.length) throw new ArrayIndexOutOfBoundsException(len);
		int clen = len;
		if (this.tail + clen > this.array.length) clen = this.array.length - this.tail;
		System.arraycopy(this.array, this.tail, buf, off, clen);
		off += clen;
		len -= clen;
		this.tail += clen;
		this.tail %= this.array.length;
		if (len > 0) System.arraycopy(this.array, 0, buf, off, len);
		this.tail += len;
	}

	public int get(int idx)
	{
		if (idx >= this.length()) return -1;
		return this.array[(this.tail + idx) % this.array.length];
	}

	public void trim(int len)
	{
		if (len > this.length()) len = this.length();
		this.head += this.array.length;
		this.head -= len;
		this.head %= this.array.length;
	}

	public void expand(int cap)
	{
		if (this.capacity() <= cap) return;
		int oldCap = this.array.length - 1;
		while (oldCap < cap) oldCap *= 2;
		byte[] newArray = new byte[oldCap + 1];

		int length = this.length();
		int len = length;
		if (this.tail + length > this.array.length)
		{
			len = this.array.length - this.tail;
		}
		System.arraycopy(this.array, this.tail, newArray, 0, len);

		length -= len;
		if (length > 0)
		{
			System.arraycopy(this.array, 0, newArray, len, length);
		}

		length = this.length();
		this.array = newArray;
		this.tail = 0;
		this.head = length;
	}

	public int length()
	{
		if (this.head >= this.tail) return this.head - this.tail;

		return (this.array.length - this.tail) + this.head;
	}

	public int capacity()
	{
		return this.array.length - 1;
	}
}
