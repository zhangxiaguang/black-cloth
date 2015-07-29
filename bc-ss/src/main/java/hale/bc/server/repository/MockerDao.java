package hale.bc.server.repository;

import hale.bc.server.repository.exception.DuplicatedEntryException;
import hale.bc.server.to.Mocker;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class MockerDao {

	private StringRedisTemplate stringTemplate;
	
	private ZSetOperations<String, String> ownerIndex;
	private ValueOperations<String, String> ownerNames;
	private ZSetOperations<String, String> publicIndex;
	private ValueOperations<String, String> publicNames;
	private ZSetOperations<String, String> collectOwnerIndex;
	private ZSetOperations<String, String> collectMockerIndex;
	private ValueOperations<String, Mocker> mockers;
	private RedisAtomicLong mockerIdGenerator;
	
	@Autowired
	public MockerDao(RedisTemplate<String, Mocker> mockerTemplate, StringRedisTemplate template) {
		stringTemplate = template;
		mockers = mockerTemplate.opsForValue();
		ownerNames = stringTemplate.opsForValue();
		ownerIndex = stringTemplate.opsForZSet();
		publicNames = stringTemplate.opsForValue();
		publicIndex = stringTemplate.opsForZSet();
		collectOwnerIndex = stringTemplate.opsForZSet();
		collectMockerIndex = stringTemplate.opsForZSet();
		mockerIdGenerator = new RedisAtomicLong(KeyUtils.mockerId(), stringTemplate.getConnectionFactory());
	}
	
	public Mocker createMocker(Mocker mocker) throws DuplicatedEntryException {
		String mockerOwnerNameKey = getOwnerNameKey(mocker);
		checkMockerName(mockerOwnerNameKey);
			
		long mid = mockerIdGenerator.incrementAndGet();
		mocker.setId(mid);
		mocker.setCreated(new Date());
		mocker.setUpdated(new Date());
		String mockerId = String.valueOf(mid);
		
		mockers.set(KeyUtils.mockerId(mockerId), mocker);
		ownerNames.set(mockerOwnerNameKey, mockerId);
		ownerIndex.add(KeyUtils.mockerOwner(mocker.getOwner()), mocker.getName(), 0);
		
		if ("Public".equals(mocker.getType().name())) {
			String mockerPublicNameKey = getPublicNameKey(mocker);
			checkMockerName(mockerPublicNameKey);
			publicNames.set(mockerPublicNameKey, mockerId);
			publicIndex.add(KeyUtils.mockerPublic(), mocker.getName(), 0);
		}
		return mocker;
	}
	
	private String getOwnerNameKey(Mocker mocker)  {
		return KeyUtils.mockerOwnerName(mocker.getOwner(), mocker.getName());
	}
	
	private String getPublicNameKey(Mocker mocker)  {
		return KeyUtils.mockerPublicName(mocker.getName());
	}
	
	private void checkMockerName(String mockerOwnerNameKey) throws DuplicatedEntryException {
		if (stringTemplate.hasKey(mockerOwnerNameKey)) {
			throw new DuplicatedEntryException(mockerOwnerNameKey);
		}
	}
	
	public Mocker getMockerById(Long mockerId) {
		return mockers.get(KeyUtils.mockerId(String.valueOf(mockerId)));
	}
	
	public Mocker getMockerById(Long mockerId, String owner) {
		Mocker m = mockers.get(KeyUtils.mockerId(String.valueOf(mockerId)));
		if (m != null && m.getOwner() != null && (m.getOwner().equals(owner) || "Public".equals(m.getType().name()))) {
			m.setCollectCount(getCollectCount(m.getId()));
			return m;
		}
		return null;
	}

	public List<Mocker> getMockers(String owner) {
		List<Mocker> result = new ArrayList<>();
		for (String mockerName : ownerIndex.range(KeyUtils.mockerOwner(owner), 0, -1)){
			Mocker m = mockers.get(KeyUtils.mockerId(ownerNames.get(KeyUtils.mockerOwnerName(owner, mockerName))));
			if (m != null) {
				m.setCollectCount(getCollectCount(m.getId()));
				result.add(m);
			}
		}
		return result;
	}
	
	public List<Mocker> getMockersByPublic(String username) {
		List<Mocker> result = new ArrayList<>();
		for (String mockerName : publicIndex.range(KeyUtils.mockerPublic(), 0, -1)){
			Mocker m = mockers.get(KeyUtils.mockerId(publicNames.get(KeyUtils.mockerPublicName(mockerName))));
			if (m != null && !username.equals(m.getOwner())) {
				m.setCollectCount(getCollectCount(m.getId()));
				result.add(m);
			}
		}
		return result;
	}
	
	public List<Mocker> getCollectMockers(String owner) {
		List<Mocker> result = new ArrayList<>();
		for (String mockerId : collectOwnerIndex.range(KeyUtils.mockerCollectOwner(owner), 0, -1)){
			Mocker m = mockers.get(KeyUtils.mockerId(mockerId));
			if (m != null) {
				m.setCollectCount(getCollectCount(m.getId()));
				result.add(m);
			}
		}
		return result;
	}
	
	public Mocker deleteMocker(Long mockerId) {
		Mocker m = getMockerById(mockerId);
		if (m == null) {
			return null;
		}
		stringTemplate.delete(getOwnerNameKey(m));
		ownerIndex.remove(KeyUtils.mockerOwner(m.getOwner()), m.getName());
		stringTemplate.delete(KeyUtils.mockerId(String.valueOf(mockerId)));
		if ("Public".equals(m.getType().name())) {
			stringTemplate.delete(getPublicNameKey(m));
			publicIndex.remove(KeyUtils.mockerPublic(), m.getName());
		}
		mockers.set(KeyUtils.mockerId(String.valueOf(-mockerId)), m);
		return m;
	}
	
	public Mocker updateMocker(Mocker mocker) throws DuplicatedEntryException {
		Long mockerId = mocker.getId();
		String mockerIdText = String.valueOf(mockerId);
		Mocker oldMocker = getMockerById(mockerId);
		if (oldMocker == null) {
			return null;
		}
		if (!oldMocker.getName().equals(mocker.getName())){
			String newOwnerNameKey = getOwnerNameKey(mocker);
			checkMockerName(newOwnerNameKey);
			
			stringTemplate.delete(getOwnerNameKey(oldMocker));
			ownerNames.set(newOwnerNameKey, mockerIdText);
			
			String ownerKey = KeyUtils.mockerOwner(mocker.getOwner());
			ownerIndex.remove(ownerKey, oldMocker.getName());
			ownerIndex.add(ownerKey, mocker.getName(), 0);
			
			if ("Public".equals(mocker.getType().name())) {
				String newMockerPublicNameKey = getPublicNameKey(mocker);
				checkMockerName(newMockerPublicNameKey);
				stringTemplate.delete(getPublicNameKey(oldMocker));
				publicNames.set(newMockerPublicNameKey, mockerId.toString());
				
				publicIndex.remove(KeyUtils.mockerPublic(), oldMocker.getName());
				publicIndex.add(KeyUtils.mockerPublic(), mocker.getName(), 0);
			}
		}
		mocker.setUpdated(new Date());
		mockers.set(KeyUtils.mockerId(mockerIdText), mocker);
		return mocker;
	}

	public Mocker collectMockerById(Long mid, String username) throws DuplicatedEntryException {
		Mocker mocker = getMockerById(mid);
		if (mocker == null) {
			return null;
		}
		String mIdStr = String.valueOf(mid);

		collectOwnerIndex.add(KeyUtils.mockerCollectOwner(username), mIdStr, 0);
		
		collectMockerIndex.add(KeyUtils.collectMockerOwner(mIdStr), username, 0);
		
		return mocker;
	}
	
	public Mocker cancelCollectMockerById(Long mid, String username) {
		Mocker mocker = getMockerById(mid);
		if (mocker == null) {
			return null;
		}
		String mIdStr = String.valueOf(mid);
		
		collectOwnerIndex.remove(KeyUtils.mockerCollectOwner(username), mIdStr);
		
		collectMockerIndex.remove(KeyUtils.collectMockerOwner(mIdStr), username);
		return mocker;
	}
	
	public int getCollectCount(Long mockerId){
		int cCount = 0;
		Set<String> set = collectMockerIndex.range(KeyUtils.collectMockerOwner(String.valueOf(mockerId)), 0, -1);
		cCount = set.size();
		return cCount;
	}
	
	public Mocker updateMockerLastDate(long mid, Date updated){
		Mocker mocker = getMockerById(mid);
		if (mocker == null) {
			return null;
		}
		mocker.setUpdated(updated);
		mockers.set(KeyUtils.mockerId(String.valueOf(mid)), mocker);
		return mocker;
	}
}
