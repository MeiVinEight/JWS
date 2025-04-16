package org.mve;

public class JavaVM
{
	@SuppressWarnings("all")
	public static <T extends RuntimeException> void exception(Throwable e)
	{
		throw (T) e;
	}
}
