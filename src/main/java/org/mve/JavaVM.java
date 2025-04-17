package org.mve;

public class JavaVM
{
	@SuppressWarnings("all")
	public static <T extends Throwable> void exception(Throwable e) throws T
	{
		throw (T) e;
	}
}
