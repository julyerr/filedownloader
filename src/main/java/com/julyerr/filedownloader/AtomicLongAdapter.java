package com.julyerr.filedownloader;

import java.util.concurrent.atomic.AtomicLong;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class AtomicLongAdapter extends XmlAdapter<Long, AtomicLong> {

	@Override
	public Long marshal(AtomicLong v) throws Exception {
		return v.get();
	}

	@Override
	public AtomicLong unmarshal(Long v) throws Exception {
		return new AtomicLong(v);
	}
}
