package com.alimama.mdrill.partion.thedate;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;


import com.alimama.mdrill.index.utils.JobIndexPublic;
import com.alimama.mdrill.json.JSONArray;
import com.alimama.mdrill.json.JSONException;
import com.alimama.mdrill.json.JSONObject;
import com.alimama.mdrill.partion.MdrillPartions;
import com.alimama.mdrill.partion.MdrillPartionsInterface;
import com.alimama.mdrill.partion.StatListenerInterface;
import com.alimama.mdrill.ui.service.utils.WebServiceParams;

public class ThedatePartions implements MdrillPartionsInterface{
	private String parttype="default"; 
	public void setPartionType(String parttype)
	{
		this.parttype=parttype;
	}
	
	private SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");

	@Override
	public String[] SqlPartions(String queryStr) throws Exception {

		HashSet<String> rtn = new HashSet<String>();
		boolean isset = false;

		Long small = -1l;
		Long bigger = -1l;
		JSONArray jsonStr = new JSONArray(queryStr.trim());

		for (int j = 0; j < jsonStr.length(); j++) {
			JSONObject obj = jsonStr.getJSONObject(j);
			if (obj.has("thedate")) {
				JSONObject thedate = obj.getJSONObject("thedate");
				Integer operate = Integer.parseInt(thedate.get("operate").toString());
				String[] val = WebServiceParams.parseFqValue(thedate.getString("value"), operate).split(",");
				if (operate == 1 || operate == 5 || operate == 6|| operate == 7 || operate == 8 || operate == 9)// =,range
				{
					Long min = Long.MAX_VALUE;
					Long max = Long.MIN_VALUE;
					for (String day : val) {
						Date d = fmt.parse(day.replaceAll("-", ""));
						Long time = d.getTime();
						max = Math.max(max, time);
						min = Math.min(min, time);
						isset = true;
					}
					String[] partions = get(min, max, parttype);
					for (String partion : partions) {
						rtn.add(partion);
					}
					break;

				}

				if (operate == 13 || operate == 3)// <,<=
				{
					for (String day : val) {
						Date d = fmt.parse(day.replaceAll("-", ""));
						Long time = d.getTime() + (operate == 3 ? 0 : 1);
						small = small > 0 ? Math.min(small, time) : time;
					}
				}

				if (operate == 14 || operate == 4)// >,>=
				{
					for (String day : val) {
						Date d = fmt.parse(day.replaceAll("-", ""));
						Long time = d.getTime() - (operate == 4 ? 0 : 1);
						bigger = bigger > 0 ? Math.max(bigger, time) : time;
					}
				}

				if (bigger > 0 && small > 0) {
					isset = true;
					String[] partions = get(small, bigger, parttype);
					for (String partion : partions) {
						rtn.add(partion);
					}
					small = Long.MAX_VALUE;
					bigger = Long.MIN_VALUE;
					break;
				}

			}
		}

		if (isset) {
			String[] rtnarr = new String[rtn.size()];
			return rtn.toArray(rtnarr);
		}

		Long step = 1000l * 3600 * 24;
		Long max = 0l;
		Long initDate = (new Date()).getTime();
		max = initDate;
		Long min = initDate - step;
		return get(min, max, parttype);
	}
	
	public String SqlFilter(String queryStr) throws Exception
	{
		JSONArray rtn = new JSONArray(queryStr.trim());
		JSONArray jsonStr = new JSONArray(queryStr.trim());

		for (int j = 0; j < jsonStr.length(); j++) {
			JSONObject obj = jsonStr.getJSONObject(j);
			if (!obj.has("thedate")) {
				rtn.put(obj);
			}
		}
		return rtn.toString();
	 }
	
	private String[] get(Long min,Long max,String parttype) throws JSONException, ParseException
    {
		Long step=1000l*3600*24;
		Long start=min;
		HashSet<String> list=new HashSet<String>();
		SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
		while(start<=max)
		{
		    list.add(fmt.format(new Date(start)));
		    start+=step;
		}
		HashMap<String,HashSet<String>> rtn=ThedatePartionsUtils.parseDays(list,parttype);
		Set<String> ks=rtn.keySet();
		String[] rtnarr=new String[ks.size()];
		return ks.toArray(rtnarr);
    }

	
	public StatListenerInterface getStatObj() throws Exception {
		StatListenerInterface rtn=new ThedateListener();
		rtn.setPartionType(this.parttype);
		rtn.init();
		return rtn;
	}
	
	
	private String getToday()
	{
		SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
		return fmt.format(new Date());
	}


	private String getMonth(String str) {
		return str.substring(0, 6);
	}

	private Date transDay(String str) throws ParseException {
		SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
		return fmt.parse(str);
	}
	
	
	

	private String DatePlus(String str, int day) throws ParseException {
		SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
		Date d = fmt.parse(str);
		Date pd = new Date();
		pd.setTime(d.getTime() - 1000l * 3600 * 24 * day);
		return fmt.format(pd);
	}
	private String getStartDay( int delay,int maxRunDays, String starday) throws ParseException
	{
		String minday = this.DatePlus(this.getToday(), maxRunDays+delay);
		Date min = this.transDay(minday);
		Date start = this.transDay(starday);
		if(min.compareTo(start)>=0)
		{
			return minday;
		}
		
		return starday;
	}
	
	
	

	
	private HashSet<String> getNameList(FileSystem fs, String inputBase,
			String startPoint) throws IOException, ParseException {
		HashSet<String> rtn = new HashSet<String>();
		Date start = this.transDay(startPoint);
		FileStatus[] list = fs.listStatus(new Path(inputBase));
		if (list != null) {
			for (FileStatus f : list) {
				String name = f.getPath().getName().trim();
				name = JobIndexPublic.parseThedate(name);
				if (name != null &&!name.isEmpty()&& this.transDay(name).compareTo(start) >= 0) {
					rtn.add(name);
				}
			}
		}

		return rtn;
	}
	

	
	public HashSet<String> getNameList(FileSystem fs,String inputBase,String startday,int dayDelay, int maxRunDays) throws Exception{
		String startyyyymmddd=this.getStartDay(dayDelay, maxRunDays, startday);
		return getNameList(fs,inputBase,startyyyymmddd);
	}
	public HashMap<String,HashSet<String>> indexPartions(HashSet<String> namelist,String startday,int dayDelay, int maxRunDays) throws Exception{
		return ThedatePartionsUtils.parseDays(namelist,this.parttype);
	}
	
	public String getUpdateFinishDay( int delay,String starday) throws ParseException
	{
		String minday = this.DatePlus(this.getToday(),delay);
		Date min = this.transDay(minday);
		Date start = this.transDay(starday);

		if(min.compareTo(start)>=0)
		{
			return minday;
		}
		
		return starday;
	}
	
	public HashMap<String,String> indexVertify(HashMap<String,HashSet<String>> partions,int shards,String startday,int dayDelay, int maxRunDays) throws Exception{
		String upFinishyyyymmdd=this.getUpdateFinishDay(dayDelay,startday);

		HashMap<String,String> rtn=new HashMap<String,String>();
		for(Entry<String,HashSet<String>> e:partions.entrySet())
		{
			String partion=e.getKey();
			HashSet<String> days=e.getValue();
			boolean writeDay=false;
			for(String day:days)
			{
				if(day.compareTo(upFinishyyyymmdd)>=0)
				{
					writeDay=true;
					break;
				}
			}
			
			String partionvertify = "partionV"+MdrillPartions.PARTION_VERSION+"@001@"+partion + "@" + shards + "@"+ days.size()+"@"+days.hashCode();
			if(writeDay)
			{
				partionvertify+="@"+this.getToday()+"@"+upFinishyyyymmdd;
			}
			
			rtn.put(partion, partionvertify);
			
		}
		
		return rtn;
	}

	
	
	 
	 
	
}
